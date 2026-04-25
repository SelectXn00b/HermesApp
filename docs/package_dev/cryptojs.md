# API 文档：`cryptojs.d.ts`

`cryptojs.d.ts` 描述的是全局 `CryptoJS` 对象的可用子集。它不是完整的上游 CryptoJS，而是应用内通过原生桥接暴露出来的一部分能力。

## 作用

当前类型定义主要覆盖两类用途：

- 计算 `MD5`。
- 使用 `AES.decrypt()` 做解密。

## 全局对象

```ts
CryptoJS
```

可以直接使用，无需 `import`。

## 可用类型与成员

### `CryptoJS.WordArray`

桥接后的结果对象，核心能力是：

```ts
toString(encoding?: any): string
```

常见写法：

```ts
const hex = CryptoJS.MD5("hello").toString();
```

### `CryptoJS.MD5(message)`

```ts
MD5(message: string): WordArray
```

对字符串做 MD5 计算，返回 `WordArray`。

### `CryptoJS.AES.decrypt(ciphertext, key, cfg?)`

```ts
CryptoJS.AES.decrypt(ciphertext: string, key: any, cfg?: any): WordArray
```

说明：

- 这里的 `key` 与 `cfg` 由桥接实现解释。
- 类型定义里没有提供 `AES.encrypt()`，因此文档也不应假设它可用。

### 编码与模式相关对象

可用成员如下：

- `CryptoJS.enc.Hex.parse(hexStr)`
- `CryptoJS.enc.Utf8`
- `CryptoJS.pad.Pkcs7`
- `CryptoJS.mode.ECB`

## 示例

### 计算 MD5

```ts
const digest = CryptoJS.MD5("assistance").toString();
complete({ digest });
```

### 将十六进制字符串转为 `WordArray`

```ts
const wordArray = CryptoJS.enc.Hex.parse("48656c6c6f");
const text = wordArray.toString(CryptoJS.enc.Utf8);
```

### AES 解密

```ts
const result = CryptoJS.AES.decrypt(ciphertext, key, {
  mode: CryptoJS.mode.ECB,
  padding: CryptoJS.pad.Pkcs7
});
const plaintext = result.toString(CryptoJS.enc.Utf8);
```

## 注意事项

- 该定义文件描述的是“当前桥接层实际暴露的 API”，不是完整版 CryptoJS。
- 如果某个上游 CryptoJS 方法没有出现在 `examples/types/cryptojs.d.ts`，就不要在脚本里假设它存在。
- `toString()` 的编码参数在桥接层里更像提示值，真正处理逻辑由原生侧决定。

## 相关文件

- `examples/types/cryptojs.d.ts`
- `examples/types/index.d.ts`
