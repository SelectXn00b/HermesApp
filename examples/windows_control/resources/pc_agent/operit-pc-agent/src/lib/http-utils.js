const fs = require("fs");
const path = require("path");

function readJsonBody(req, options = {}) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    const maxBodyBytes =
      typeof options.maxBodyBytes === "number" && options.maxBodyBytes > 0
        ? Math.floor(options.maxBodyBytes)
        : 1024 * 1024;

    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > maxBodyBytes) {
        reject(new Error("Payload too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });

    req.on("end", () => {
      if (chunks.length === 0) {
        resolve({});
        return;
      }

      try {
        const text = Buffer.concat(chunks).toString("utf8");
        resolve(JSON.parse(text));
      } catch {
        reject(new Error("Invalid JSON body"));
      }
    });

    req.on("error", (err) => reject(err));
  });
}

function sendJson(res, statusCode, data) {
  const payload = JSON.stringify(data);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  res.end(payload);
}

function sendFile(res, filePath, contentType) {
  if (!fs.existsSync(filePath)) {
    sendNotFound(res);
    return;
  }

  const content = fs.readFileSync(filePath);
  res.writeHead(200, {
    "Content-Type": contentType,
    "Cache-Control": "no-store"
  });
  res.end(content);
}

function sendNotFound(res) {
  res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
  res.end("Not Found");
}

function parseBoolean(value, defaultValue) {
  if (value === undefined || value === null || value === "") {
    return defaultValue;
  }

  const text = String(value).trim().toLowerCase();
  return text === "true" || text === "1" || text === "yes" || text === "on";
}

function createStaticFileServer({ publicDir, staticContentTypes }) {
  function getContentType(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    return staticContentTypes[ext] || "application/octet-stream";
  }

  function tryServePublicFile(res, requestPath) {
    if (!requestPath) {
      return false;
    }

    let decodedPath = requestPath;
    try {
      decodedPath = decodeURIComponent(requestPath);
    } catch {
      return false;
    }

    const finalPath = decodedPath === "/" ? "/index.html" : decodedPath;
    const resolvedPath = path.resolve(publicDir, "." + finalPath);
    const relativePath = path.relative(publicDir, resolvedPath);

    if (relativePath.startsWith("..") || path.isAbsolute(relativePath)) {
      return false;
    }

    if (!fs.existsSync(resolvedPath) || fs.statSync(resolvedPath).isDirectory()) {
      return false;
    }

    sendFile(res, resolvedPath, getContentType(resolvedPath));
    return true;
  }

  return {
    tryServePublicFile
  };
}

module.exports = {
  readJsonBody,
  sendJson,
  sendNotFound,
  parseBoolean,
  createStaticFileServer
};
