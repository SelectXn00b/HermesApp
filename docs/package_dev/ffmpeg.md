# API 文档：`ffmpeg.d.ts`

`ffmpeg.d.ts` 为包内脚本提供了 `Tools.FFmpeg` 命名空间，以及若干编解码参数类型。

## 作用

适合处理以下任务：

- 执行原始 FFmpeg 命令。
- 查询当前 FFmpeg 环境信息。
- 用简化参数完成常见视频转码。

## 类型别名

### `FFmpegVideoCodec`

视频编码枚举值包括：

- `h264`
- `hevc`
- `vp8`
- `vp9`
- `av1`
- `libx265`
- `libvpx`
- `libaom`
- `mpeg4`
- `mjpeg`
- `prores`

### `FFmpegAudioCodec`

音频编码枚举值包括：

- `aac`
- `mp3`
- `opus`
- `vorbis`
- `flac`
- `pcm`
- `wav`
- `ac3`
- `eac3`

### `FFmpegResolution`

支持：

- 预设值：`1280x720`、`1920x1080`、`3840x2160`、`7680x4320`
- 自定义值：`${number}x${number}`

### `FFmpegBitrate`

支持：

- 预设值：`500k`、`1000k`、`2000k`、`4000k`、`8000k`
- 自定义值：`${number}k`、`${number}M`

## 运行时 API

### `Tools.FFmpeg.execute(command)`

```ts
execute(command: string): Promise<FFmpegResultData>
```

说明：

- `command` 只传 FFmpeg 参数本身。
- 不要把前缀 `ffmpeg` 也写进去。

示例：

```ts
await Tools.FFmpeg.execute('-i input.mp4 -vf scale=1280:720 output.mp4');
```

### `Tools.FFmpeg.info()`

```ts
info(): Promise<FFmpegResultData>
```

返回当前 FFmpeg 可执行环境信息。

### `Tools.FFmpeg.convert(inputPath, outputPath, options?)`

```ts
convert(
  inputPath: string,
  outputPath: string,
  options?: {
    video_codec?: FFmpegVideoCodec;
    audio_codec?: FFmpegAudioCodec;
    resolution?: FFmpegResolution;
    bitrate?: FFmpegBitrate;
  }
): Promise<FFmpegResultData>
```

适合最常见的“输入文件 → 输出文件”转码场景。

## 示例

### 简单转码

```ts
const result = await Tools.FFmpeg.convert(
  '/sdcard/input.mp4',
  '/sdcard/output.mp4',
  {
    video_codec: 'h264',
    audio_codec: 'aac',
    resolution: '1920x1080',
    bitrate: '4000k'
  }
);
complete(result);
```

### 查询环境

```ts
const info = await Tools.FFmpeg.info();
console.log(info.toString());
```

## 返回值

三个 API 都返回 `FFmpegResultData`。该类型定义在 `results.d.ts` 中，包含执行结果与可读化输出。

## 相关文件

- `examples/types/ffmpeg.d.ts`
- `examples/types/results.d.ts`
- `examples/types/index.d.ts`
