# API 文档：`jimp.d.ts`

`jimp.d.ts` 描述的是全局 `Jimp` 对象当前在运行环境中可用的桥接能力。它是一个轻量子集，重点覆盖基础图片读写与简单图像处理。

## 作用

当前定义支持：

- 从 Base64 读取图片。
- 创建空白图片。
- 裁剪与合成。
- 导出为 Base64。
- 手动释放桥接对象。

## 全局对象

```ts
Jimp
```

可以直接使用，无需 `import`。

## 核心类型

### `Jimp.JimpWrapper`

桥接后的图片对象，包含以下方法：

- `crop(x, y, w, h)`：裁剪并返回新的 `JimpWrapper`。
- `composite(src, x, y)`：把另一张图叠加到当前图上。
- `getWidth()`：获取宽度。
- `getHeight()`：获取高度。
- `getBase64(mime)`：导出为 Base64 字符串。
- `release()`：释放底层资源。

### `Jimp.read(base64)`

```ts
read(base64: string): Promise<JimpWrapper>
```

从 Base64 内容创建图片对象。

### `Jimp.create(w, h)`

```ts
create(w: number, h: number): Promise<JimpWrapper>
```

创建一张空白图片。

### MIME 常量

- `Jimp.MIME_JPEG`
- `Jimp.MIME_PNG`

## 示例

### 读取图片并裁剪

```ts
const image = await Jimp.read(base64Image);
const cropped = await image.crop(0, 0, 200, 200);
const output = await cropped.getBase64(Jimp.MIME_PNG);
await image.release();
await cropped.release();
```

### 创建画布并叠加图片

```ts
const canvas = await Jimp.create(800, 600);
const icon = await Jimp.read(iconBase64);
await canvas.composite(icon, 24, 24);
const merged = await canvas.getBase64(Jimp.MIME_PNG);
await icon.release();
await canvas.release();
```

## 使用建议

- `JimpWrapper` 背后是桥接对象，使用完后尽量调用 `release()`。
- 输入是 Base64，不是文件路径；如果你手头是文件，可以先配合 `Tools.Files.readBinary()` 获取内容。
- 文档只覆盖 `examples/types/jimp.d.ts` 里明确声明的方法，不应假设存在完整版 Jimp 的所有 API。

## 相关文件

- `examples/types/jimp.d.ts`
- `examples/types/files.d.ts`
- `examples/types/index.d.ts`
