#!/usr/bin/env python3
"""Simple client to connect to the Shower WebSocket server and create a virtual display.

Usage examples (run from tools directory):

    python shower_ws_client.py
    python shower_ws_client.py --width 1280 --height 720 --dpi 320 --duration 30

Requirements:
    pip install websocket-client

By default this script will run `adb forward tcp:8986 tcp:8986` so that
it can connect to the WebSocket server started on the device by
run_shower_server.bat.
"""

import argparse
import subprocess
import sys
import time
from typing import Optional

try:
    import websocket  # type: ignore
except ImportError:  # pragma: no cover
    print("[ERROR] Missing dependency: websocket-client", file=sys.stderr)
    print("        Please run: pip install websocket-client", file=sys.stderr)
    sys.exit(1)

try:  # optional decode/display stack
    import av  # type: ignore
    import numpy as np  # type: ignore
    import cv2  # type: ignore
    HAS_DECODE = True
except Exception:  # pragma: no cover
    HAS_DECODE = False


mouse_state = {
    "down": False,
    "start": (0.0, 0.0),
    "end": (0.0, 0.0),
    "pending": None,
}

log_lines = []


def _maybe_avcc_to_annexb(packet: bytes) -> bytes:
    """Convert length-prefixed (AVCC) NAL units to Annex-B if necessary.

    If the packet already looks like Annex-B (starts with 0x00000001 or 0x000001),
    it is returned unchanged.
    """

    if len(packet) >= 4:
        # 0x00000001 or 0x000001?? pattern
        if packet[0] == 0 and packet[1] == 0 and ((packet[2] == 0 and packet[3] == 1) or packet[2] == 1):
            return packet

    out = bytearray()
    i = 0
    n = len(packet)
    while i + 4 <= n:
        nal_len = int.from_bytes(packet[i:i + 4], "big")
        i += 4
        if nal_len <= 0 or i + nal_len > n:
            # Not a valid AVCC packet, return original
            return packet
        out += b"\x00\x00\x00\x01"
        out += packet[i:i + nal_len]
        i += nal_len

    return bytes(out) if out else packet


def ensure_adb_forward(port: int) -> None:
    """Ensure adb forward is set up for the given port.

    This mirrors scrcpy-style behavior: host tcp:port -> device tcp:port.
    """
    try:
        result = subprocess.run(
            ["adb", "forward", f"tcp:{port}", f"tcp:{port}"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            print("[WARN] adb forward failed:", result.stderr.strip(), file=sys.stderr)
        else:
            print("[INFO] adb forward tcp:{0} -> tcp:{0} set".format(port))
    except FileNotFoundError:
        print("[WARN] adb not found in PATH, skipping port forwarding", file=sys.stderr)
    except Exception as e:  # pragma: no cover
        print("[WARN] adb forward raised exception:", e, file=sys.stderr)


def connect_and_run(host: str, port: int, width: int, height: int, dpi: int, duration: float,
                    launch_package: Optional[str], tap: Optional[list], swipe: Optional[list],
                    keycode: Optional[int], window_scale: float, bitrate_kbps: Optional[int]) -> None:
    url = f"ws://{host}:{port}"
    print(f"[INFO] Connecting to {url} ...")
    ws: Optional[websocket.WebSocket] = None
    try:
        ws = websocket.create_connection(url, timeout=5)
        print("[INFO] Connected")

        cmd = f"CREATE_DISPLAY {width} {height} {dpi}"
        if bitrate_kbps is not None and bitrate_kbps > 0:
            cmd += f" {int(bitrate_kbps)}"
        print("[INFO] Sending:", cmd)
        ws.send(cmd)

        if launch_package:
            launch_cmd = f"LAUNCH_APP {launch_package}"
            print("[INFO] Sending:", launch_cmd)
            ws.send(launch_cmd)

        if tap is not None:
            try:
                x, y = tap
                tap_cmd = f"TAP {x} {y}"
                print("[INFO] Sending:", tap_cmd)
                ws.send(tap_cmd)
            except Exception as e:
                print("[WARN] Invalid --tap values:", e, file=sys.stderr)

        if swipe is not None:
            try:
                x1, y1, x2, y2, dur = swipe
                swipe_cmd = f"SWIPE {x1} {y1} {x2} {y2} {int(dur)}"
                print("[INFO] Sending:", swipe_cmd)
                ws.send(swipe_cmd)
            except Exception as e:
                print("[WARN] Invalid --swipe values:", e, file=sys.stderr)

        if keycode is not None:
            key_cmd = f"KEY {keycode}"
            print("[INFO] Sending:", key_cmd)
            ws.send(key_cmd)

        # Use a small timeout so that ws.recv() does not block the UI loop for long.
        # This makes the OpenCV windows more responsive at the cost of a slightly busier loop.
        ws.settimeout(0.02)
        start = time.time()
        frame_count = 0

        decoder = None
        window_name = "Shower Virtual Display"
        log_window_name = "Shower Logs"
        if HAS_DECODE:
            try:
                decoder = av.CodecContext.create("h264", "r")
                print("[INFO] Decode/display enabled (PyAV + OpenCV)")
                print("       Close the window or press Ctrl+C in terminal to stop.")

                disp_w = max(1, int(width * window_scale))
                disp_h = max(1, int(height * window_scale))
                scale_x = width / float(disp_w)
                scale_y = height / float(disp_h)

                def on_mouse(event, x, y, flags, param):
                    if event == cv2.EVENT_LBUTTONDOWN:
                        mouse_state["down"] = True
                        mouse_state["start"] = (float(x), float(y))
                        mouse_state["end"] = (float(x), float(y))
                        dev_x = max(0.0, min(float(width - 1), float(x) * scale_x))
                        dev_y = max(0.0, min(float(height - 1), float(y) * scale_y))
                        mouse_state["pending"] = ("touch_down", dev_x, dev_y)
                    elif event == cv2.EVENT_MOUSEMOVE and mouse_state["down"]:
                        mouse_state["end"] = (float(x), float(y))
                        dev_x = max(0.0, min(float(width - 1), float(x) * scale_x))
                        dev_y = max(0.0, min(float(height - 1), float(y) * scale_y))
                        mouse_state["pending"] = ("touch_move", dev_x, dev_y)
                    elif event == cv2.EVENT_LBUTTONUP:
                        mouse_state["down"] = False
                        mouse_state["end"] = (float(x), float(y))
                        dev_x = max(0.0, min(float(width - 1), float(x) * scale_x))
                        dev_y = max(0.0, min(float(height - 1), float(y) * scale_y))
                        mouse_state["pending"] = ("touch_up", dev_x, dev_y)

                cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
                cv2.resizeWindow(window_name, disp_w, disp_h)
                cv2.setMouseCallback(window_name, on_mouse)

                cv2.namedWindow(log_window_name, cv2.WINDOW_NORMAL)
                cv2.resizeWindow(log_window_name, 600, 400)
            except Exception as e:
                print("[WARN] Failed to init decoder:", e, file=sys.stderr)
                decoder = None

        print(f"[INFO] Receiving for ~{duration} seconds (Ctrl+C to stop early)...")
        running = True
        while running:
            elapsed = time.time() - start
            remaining = max(0.0, duration - elapsed)
            if remaining <= 0:
                break

            msg = None
            try:
                msg = ws.recv()
            except websocket.WebSocketTimeoutException:
                # No message this iteration, just fall through to pump UI events
                msg = None
            except KeyboardInterrupt:
                print("[INFO] Interrupted by user")
                break

            if isinstance(msg, bytes):
                if decoder is not None:
                    try:
                        data = _maybe_avcc_to_annexb(msg)
                        packet = av.packet.Packet(data)
                        decode_start = time.time()
                        frames = decoder.decode(packet)
                        for f in frames:
                            img = f.to_ndarray(format="bgr24")
                            if window_scale != 1.0:
                                img_disp = cv2.resize(img, (disp_w, disp_h))
                            else:
                                img_disp = img

                            # draw countdown at top-right
                            h, w = img_disp.shape[:2]
                            text = f"{remaining:4.1f}s"
                            ((tw, th), _) = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 1)
                            x = max(0, w - tw - 10)
                            y = max(th + 5, 20)
                            cv2.putText(
                                img_disp,
                                text,
                                (x, y),
                                cv2.FONT_HERSHEY_SIMPLEX,
                                0.6,
                                (0, 255, 255),
                                1,
                                cv2.LINE_AA,
                            )

                            cv2.imshow(window_name, img_disp)

                            # Avoid spending too much time on a single batch of frames; drop extras if needed.
                            if time.time() - decode_start > 0.02:
                                break
                    except KeyboardInterrupt:
                        print("[INFO] Interrupted by user (window)")
                        break
                    except Exception as e:
                        print("[WARN] Decode error:", e, file=sys.stderr)
                else:
                    frame_count += 1
                    # Print every 30th frame to avoid too much spam
                    if frame_count % 30 == 0:
                        print(f"[INFO] Received binary frame #{frame_count}, size={len(msg)} bytes")
            elif isinstance(msg, str):
                text = msg
                print("[INFO] Text message:", text)
                log_lines.append(text)
                if len(log_lines) > 100:
                    del log_lines[:-100]

            if mouse_state["pending"] is not None and ws is not None:
                kind, *vals = mouse_state["pending"]
                if kind == "tap":
                    x, y = vals
                    tap_cmd = f"TAP {x} {y}"
                    print("[INFO] Sending:", tap_cmd)
                    try:
                        ws.send(tap_cmd)
                    except Exception as e:
                        print("[WARN] Failed to send TAP:", e, file=sys.stderr)
                elif kind == "swipe":
                    x1, y1, x2, y2, dur = vals
                    swipe_cmd = f"SWIPE {x1} {y1} {x2} {y2} {int(dur)}"
                    print("[INFO] Sending:", swipe_cmd)
                    try:
                        ws.send(swipe_cmd)
                    except Exception as e:
                        print("[WARN] Failed to send SWIPE:", e, file=sys.stderr)
                elif kind == "touch_down":
                    x, y = vals
                    cmd = f"TOUCH_DOWN {x} {y}"
                    print("[INFO] Sending:", cmd)
                    try:
                        ws.send(cmd)
                    except Exception as e:
                        print("[WARN] Failed to send TOUCH_DOWN:", e, file=sys.stderr)
                elif kind == "touch_move":
                    x, y = vals
                    cmd = f"TOUCH_MOVE {x} {y}"
                    # 不必每一帧都打印，避免刷屏
                    try:
                        ws.send(cmd)
                    except Exception as e:
                        print("[WARN] Failed to send TOUCH_MOVE:", e, file=sys.stderr)
                elif kind == "touch_up":
                    x, y = vals
                    cmd = f"TOUCH_UP {x} {y}"
                    print("[INFO] Sending:", cmd)
                    try:
                        ws.send(cmd)
                    except Exception as e:
                        print("[WARN] Failed to send TOUCH_UP:", e, file=sys.stderr)
                mouse_state["pending"] = None

            if HAS_DECODE and decoder is not None:
                try:
                    if log_lines:
                        img_h, img_w = 400, 600
                        img = np.zeros((img_h, img_w, 3), dtype=np.uint8)
                        line_h = 16
                        start_idx = max(0, len(log_lines) - img_h // line_h)
                        y = line_h
                        for line in log_lines[start_idx:]:
                            cv2.putText(
                                img,
                                line[-80:],
                                (5, y),
                                cv2.FONT_HERSHEY_SIMPLEX,
                                0.4,
                                (0, 255, 0),
                                1,
                                cv2.LINE_AA,
                            )
                            y += line_h
                            if y > img_h:
                                break
                        cv2.imshow(log_window_name, img)

                    key = cv2.waitKey(1) & 0xFF
                    if key == ord("q"):
                        print("[INFO] Quit requested from keyboard")
                        break
                    try:
                        if cv2.getWindowProperty(window_name, cv2.WND_PROP_VISIBLE) < 1:
                            print("[INFO] Video window closed by user")
                            break
                        if cv2.getWindowProperty(log_window_name, cv2.WND_PROP_VISIBLE) < 1:
                            print("[INFO] Log window closed by user")
                            break
                    except Exception:
                        break
                except Exception as e:
                    print("[WARN] Failed to render log window:", e, file=sys.stderr)

    except Exception as e:
        print("[ERROR] WebSocket error:", e, file=sys.stderr)
    finally:
        if ws is not None:
            try:
                print("[INFO] Sending DESTROY_DISPLAY and closing...")
                try:
                    ws.send("DESTROY_DISPLAY")
                except Exception:
                    pass
                ws.close()
            except Exception as e:  # pragma: no cover
                print("[WARN] Error while closing WebSocket:", e, file=sys.stderr)

        if HAS_DECODE:
            try:
                cv2.destroyAllWindows()
            except Exception:
                pass

        print("[INFO] Done.")


def main() -> None:
    parser = argparse.ArgumentParser(description="Shower WebSocket virtual display client")
    parser.add_argument("--host", default="127.0.0.1", help="WebSocket host (default: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=8986, help="WebSocket port (default: 8986)")
    parser.add_argument("--width", type=int, default=1080, help="Virtual display width")
    parser.add_argument("--height", type=int, default=1920, help="Virtual display height")
    parser.add_argument("--dpi", type=int, default=320, help="Virtual display dpi")
    parser.add_argument("--duration", type=float, default=10.0, help="Seconds to keep receiving video")
    parser.add_argument("--launch-package", dest="launch_package", default=None,
                        help="Optional package name to launch on the virtual display (LAUNCH_APP command)")
    parser.add_argument("--tap", nargs=2, type=float, metavar=("X", "Y"),
                        help="Send a TAP command at (X, Y) in display coordinates")
    parser.add_argument("--swipe", nargs=5, type=float,
                        metavar=("X1", "Y1", "X2", "Y2", "DUR_MS"),
                        help="Send a SWIPE command from (X1,Y1) to (X2,Y2) over DUR_MS milliseconds")
    parser.add_argument("--key", dest="keycode", type=int,
                        help="Send a KEY command with the given Android keyCode (e.g. 3=HOME)")
    parser.add_argument("--window-scale", type=float, default=0.5,
                        help="Scale factor for display window size relative to virtual display (default: 0.5)")
    parser.add_argument("--bitrate", dest="bitrate_kbps", type=int, default=None,
                        help="Optional encoder bitrate in kbps (e.g. 2000 for 2Mbps). If omitted, server default is used")
    parser.add_argument(
        "--no-adb-forward",
        action="store_true",
        help="Do not run 'adb forward' automatically",
    )

    args = parser.parse_args()

    if not args.no_adb_forward and args.host == "127.0.0.1":
        ensure_adb_forward(args.port)

    connect_and_run(
        args.host,
        args.port,
        args.width,
        args.height,
        args.dpi,
        args.duration,
        args.launch_package,
        args.tap,
        args.swipe,
        args.keycode,
        args.window_scale,
        args.bitrate_kbps,
    )


if __name__ == "__main__":
    main()
