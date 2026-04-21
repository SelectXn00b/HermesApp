/**
 * Compose-like DSL type definitions for toolpkg runtime module `runtime="compose_dsl"`.
 */
import type { ComposeMaterial3GeneratedUiFactoryRegistry } from "./compose-dsl.material3.generated";

export type ComposeTextStyle =
  | "headlineSmall"
  | "headlineMedium"
  | "titleLarge"
  | "titleMedium"
  | "titleSmall"
  | "bodyLarge"
  | "bodyMedium"
  | "bodySmall"
  | "labelLarge"
  | "labelMedium"
  | "labelSmall";

export type ComposeArrangement =
  | "start"
  | "center"
  | "end"
  | "spaceBetween"
  | "spaceAround"
  | "spaceEvenly";

export type ComposeAlignment = "start" | "center" | "end";

export interface ComposeShape {
  cornerRadius?: number;
}

export interface ComposeBorder {
  width?: number;
  color?: ComposeColor;
  alpha?: number;
}

export interface ComposePadding {
  horizontal?: number;
  vertical?: number;
}

export type ComposeCanvasUnit = "px" | "dp" | "fraction";

export interface ComposeUnitValue {
  value: number;
  unit: ComposeCanvasUnit;
}

export type ComposeCanvasNumber = number | ComposeUnitValue;

export type ComposeTextOverflow = "clip" | "ellipsis";

export interface ComposeTextMeasureRequest {
  text: string;
  fontSize?: number;
  maxWidth: number;
  maxHeight?: number;
  minWidth?: number;
  minHeight?: number;
  maxLines?: number;
  overflow?: ComposeTextOverflow;
}

export interface ComposeTextMeasureResult {
  width: number;
  height: number;
}

export interface ComposeColorToken {
  __colorToken: string;
  alpha?: number;
  copy(options: { alpha: number }): ComposeColorToken;
}

export type ComposeColor = string | ComposeColorToken;

export interface ComposeColorScheme {
  [key: string]: ComposeColorToken;
}

export interface ComposeMaterialTheme {
  colorScheme: ComposeColorScheme;
}

export interface ComposeCanvasBrush {
  type: "verticalGradient";
  colors: ComposeColor[];
}

export interface ComposeCanvasTransform {
  scale?: number;
  offsetX?: number;
  offsetY?: number;
  pivotX?: number;
  pivotY?: number;
}

export interface ComposeCanvasTransformEvent {
  centroidX: number;
  centroidY: number;
  panX: number;
  panY: number;
  zoom: number;
  rotation: number;
}

export interface ComposeCanvasSizeEvent {
  width: number;
  height: number;
}

declare global {
  interface Number {
    readonly px: ComposeUnitValue;
    readonly dp: ComposeUnitValue;
    readonly fraction: ComposeUnitValue;
  }
}

export interface ComposeCanvasLineCommand {
  type: "line";
  x1: ComposeCanvasNumber;
  y1: ComposeCanvasNumber;
  x2: ComposeCanvasNumber;
  y2: ComposeCanvasNumber;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasRectCommand {
  type: "rect";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  width: ComposeCanvasNumber;
  height: ComposeCanvasNumber;
  brush?: ComposeCanvasBrush;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  filled?: boolean;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasRoundRectCommand {
  type: "roundRect";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  width: ComposeCanvasNumber;
  height: ComposeCanvasNumber;
  radius?: ComposeCanvasNumber;
  brush?: ComposeCanvasBrush;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  filled?: boolean;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasCircleCommand {
  type: "circle";
  cx: ComposeCanvasNumber;
  cy: ComposeCanvasNumber;
  radius: ComposeCanvasNumber;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  filled?: boolean;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasTextCommand {
  type: "text";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  text: string;
  color?: ComposeColor;
  alpha?: number;
  fontSize?: ComposeCanvasNumber;
  minWidth?: ComposeCanvasNumber;
  maxWidth?: ComposeCanvasNumber;
  minHeight?: ComposeCanvasNumber;
  maxHeight?: ComposeCanvasNumber;
  maxLines?: number;
  overflow?: ComposeTextOverflow;
  unit?: ComposeCanvasUnit;
}

// Path operations for drawPath command
export interface ComposeCanvasMoveToOp {
  type: "moveTo";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
}

export interface ComposeCanvasLineToOp {
  type: "lineTo";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
}

export interface ComposeCanvasCubicToOp {
  type: "cubicTo";
  x1: ComposeCanvasNumber;
  y1: ComposeCanvasNumber;
  x2: ComposeCanvasNumber;
  y2: ComposeCanvasNumber;
  x3: ComposeCanvasNumber;
  y3: ComposeCanvasNumber;
}

export interface ComposeCanvasQuadToOp {
  type: "quadTo";
  x1: ComposeCanvasNumber;
  y1: ComposeCanvasNumber;
  x2: ComposeCanvasNumber;
  y2: ComposeCanvasNumber;
}

export interface ComposeCanvasCloseOp {
  type: "close";
}

export type ComposeCanvasPathOp =
  | ComposeCanvasMoveToOp
  | ComposeCanvasLineToOp
  | ComposeCanvasCubicToOp
  | ComposeCanvasQuadToOp
  | ComposeCanvasCloseOp;

export type ComposeCanvasDrawStyle = "fill" | "stroke";

// Enhanced Canvas commands
export interface ComposeCanvasDrawPathCommand {
  type: "drawPath";
  path: ComposeCanvasPathOp[];
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  style?: ComposeCanvasDrawStyle;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasDrawRoundRectCommand {
  type: "drawRoundRect";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  width: ComposeCanvasNumber;
  height: ComposeCanvasNumber;
  cornerRadius?: ComposeCanvasNumber;
  brush?: ComposeCanvasBrush;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  style?: ComposeCanvasDrawStyle;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasDrawTextCommand {
  type: "drawText";
  text: string;
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  color?: ComposeColor;
  alpha?: number;
  fontSize?: ComposeCanvasNumber;
  fontWeight?: string;
  minWidth?: ComposeCanvasNumber;
  maxWidth?: ComposeCanvasNumber;
  minHeight?: ComposeCanvasNumber;
  maxHeight?: ComposeCanvasNumber;
  maxLines?: number;
  overflow?: ComposeTextOverflow;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasDrawIconCommand {
  type: "drawIcon";
  icon: string;
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  size?: ComposeCanvasNumber;
  color?: ComposeColor;
  alpha?: number;
  unit?: ComposeCanvasUnit;
}

export type ComposeCanvasCommand =
  | ComposeCanvasLineCommand
  | ComposeCanvasRectCommand
  | ComposeCanvasRoundRectCommand
  | ComposeCanvasCircleCommand
  | ComposeCanvasTextCommand
  | ComposeCanvasDrawPathCommand
  | ComposeCanvasDrawRoundRectCommand
  | ComposeCanvasDrawTextCommand
  | ComposeCanvasDrawIconCommand;

export interface ComposeModifierOp {
  name: string;
  args?: unknown[];
}

export interface ComposeModifierValue {
  __modifierOps: ComposeModifierOp[];
}

export type ComposeModifierProxy = ComposeModifierValue & {
  [method: string]: (...args: unknown[]) => ComposeModifierProxy;
};

export interface ComposeTextFieldStyle {
  fontSize?: number;
  fontWeight?: string;
  color?: ComposeColor;
}

export interface ComposeCommonProps {
  key?: string;
  onLoad?: () => void | Promise<void>;
  modifier?: ComposeModifierValue;
  weight?: number;
  width?: number;
  height?: number;
  padding?: number | ComposePadding;
  paddingHorizontal?: number;
  paddingVertical?: number;
  paddingBottom?: number;
  spacing?: number;
  fillMaxWidth?: boolean;
  fillMaxSize?: boolean;
  backgroundBrush?: ComposeCanvasBrush;
  backgroundShape?: ComposeShape;
}

export interface ColumnProps extends ComposeCommonProps {
  horizontalAlignment?: ComposeAlignment;
  verticalArrangement?: ComposeArrangement;
}

export interface RowProps extends ComposeCommonProps {
  horizontalArrangement?: ComposeArrangement;
  verticalAlignment?: ComposeAlignment;
  onClick?: () => void | Promise<void>;
}

export interface BoxProps extends ComposeCommonProps {
  contentAlignment?: ComposeAlignment;
}

export interface SpacerProps {
  width?: number;
  height?: number;
}

export interface TextProps extends ComposeCommonProps {
  text: string;
  style?: ComposeTextStyle;
  color?: ComposeColor;
  fontWeight?: string;
  fontSize?: number;
  maxLines?: number;
  weight?: number;
}

export interface TextFieldProps extends ComposeCommonProps {
  label?: string;
  placeholder?: string;
  value: string;
  onValueChange: (value: string) => void;
  singleLine?: boolean;
  minLines?: number;
  isPassword?: boolean;
  style?: ComposeTextFieldStyle;
}

export interface SwitchProps extends ComposeCommonProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  enabled?: boolean;
}

export interface CheckboxProps extends ComposeCommonProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  enabled?: boolean;
}

export interface ButtonProps extends ComposeCommonProps {
  text?: string;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
}

export interface IconButtonProps extends ComposeCommonProps {
  icon?: string;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
}

export interface CardProps extends ComposeCommonProps {
  containerColor?: ComposeColor;
  containerAlpha?: number;
  contentColor?: ComposeColor;
  contentAlpha?: number;
  shape?: ComposeShape;
  border?: ComposeBorder;
  elevation?: number;
}

export interface SurfaceProps extends ComposeCommonProps {
  containerColor?: ComposeColor;
  contentColor?: ComposeColor;
  shape?: ComposeShape;
  alpha?: number;
}

export interface IconProps extends ComposeCommonProps {
  name?: string;
  tint?: ComposeColor;
  size?: number;
}

export interface LazyColumnProps extends ComposeCommonProps {
  spacing?: number;
}

export interface LinearProgressIndicatorProps extends ComposeCommonProps {
  progress?: number;
}

export interface CircularProgressIndicatorProps extends ComposeCommonProps {
  strokeWidth?: number;
  color?: ComposeColor;
}

export interface SnackbarHostProps extends ComposeCommonProps {}

export interface CanvasProps extends ComposeCommonProps {
  commands?: ComposeCanvasCommand[];
  transform?: ComposeCanvasTransform;
  onTransform?: (event: ComposeCanvasTransformEvent) => void;
  onSizeChanged?: (event: ComposeCanvasSizeEvent) => void;
}

export interface ComposeNode {
  type: string;
  props?: Record<string, unknown>;
  children?: ComposeNode[];
}

export type ComposeChildren = ComposeNode | ComposeNode[] | null | undefined;

export type ComposeNodeFactory<TProps extends Record<string, unknown> = Record<string, unknown>> = (
  props?: TProps,
  children?: ComposeChildren
) => ComposeNode;

export interface ComposeUiFactoryRegistry {
  Column: ComposeNodeFactory<ColumnProps>;
  Row: ComposeNodeFactory<RowProps>;
  Box: ComposeNodeFactory<BoxProps>;
  Spacer: ComposeNodeFactory<SpacerProps>;
  Text: ComposeNodeFactory<TextProps>;
  TextField: ComposeNodeFactory<TextFieldProps>;
  Switch: ComposeNodeFactory<SwitchProps>;
  Checkbox: ComposeNodeFactory<CheckboxProps>;
  Button: ComposeNodeFactory<ButtonProps>;
  IconButton: ComposeNodeFactory<IconButtonProps>;
  Card: ComposeNodeFactory<CardProps>;
  Surface: ComposeNodeFactory<SurfaceProps>;
  Icon: ComposeNodeFactory<IconProps>;
  LazyColumn: ComposeNodeFactory<LazyColumnProps>;
  LinearProgressIndicator: ComposeNodeFactory<LinearProgressIndicatorProps>;
  CircularProgressIndicator: ComposeNodeFactory<CircularProgressIndicatorProps>;
  SnackbarHost: ComposeNodeFactory<SnackbarHostProps>;
  Canvas: ComposeNodeFactory<CanvasProps>;
}

export interface ComposeTemplateValues {
  [key: string]: string | number | boolean | null | undefined;
}

export interface ComposeUiModuleSpec {
  id?: string;
  runtime?: string;
  [key: string]: unknown;
}

export interface ComposeToolCallConfig {
  type?: string;
  name: string;
  params?: Record<string, unknown>;
}

export interface ComposeResolveToolNameRequest {
  packageName?: string;
  subpackageId?: string;
  toolName: string;
  preferImported?: boolean;
}

export interface ComposeDslContext {
  MaterialTheme: ComposeMaterialTheme;
  useState<T>(key: string, initialValue: T): [T, (value: T) => void];
  useMutable<T>(key: string, initialValue: T): [T, (value: T) => void];
  useRef<T>(key: string, initialValue: T): { current: T };
  useMemo<T>(key: string, factory: () => T, deps?: unknown[]): T;
  measureText(request: ComposeTextMeasureRequest): ComposeTextMeasureResult;

  callTool<T = any>(toolName: string, params?: Record<string, unknown>): Promise<T>;
  getEnv(key: string): string | undefined;
  setEnv(key: string, value: string): Promise<void> | void;
  navigate(route: string, args?: Record<string, unknown>): Promise<void> | void;
  showToast(message: string): Promise<void> | void;
  reportError(error: unknown): Promise<void> | void;

  /**
   * Returns runtime module spec parsed from a registered toolpkg runtime module entry.
   */
  getModuleSpec?<TSpec extends ComposeUiModuleSpec = ComposeUiModuleSpec>(): TSpec;

  /**
   * Runtime identity of current compose_dsl module.
   */
  getCurrentPackageName?(): string | undefined;
  getCurrentToolPkgId?(): string | undefined;
  getCurrentUiModuleId?(): string | undefined;

  /**
   * Formats template text like "failed: {error}" with provided values.
   */
  formatTemplate?(template: string, values: ComposeTemplateValues): string;

  /**
   * Batch environment writes; host may implement atomically.
   */
  setEnvs?(values: Record<string, string>): Promise<void> | void;

  /**
   * Optional toolCall-compatible bridge so compose_dsl script can use package-tool style calls.
   */
  toolCall?<T = unknown>(toolName: string, toolParams?: Record<string, unknown>): Promise<T>;
  toolCall?<T = unknown>(
    toolType: string,
    toolName: string,
    toolParams?: Record<string, unknown>
  ): Promise<T>;
  toolCall?<T = unknown>(config: ComposeToolCallConfig): Promise<T>;

  /**
   * Package-manager bridge methods.
   */
  isPackageImported?(packageName: string): Promise<boolean> | boolean;
  importPackage?(packageName: string): Promise<string> | string;
  removePackage?(packageName: string): Promise<string> | string;
  usePackage?(packageName: string): Promise<string> | string;
  listImportedPackages?(): Promise<string[]> | string[];

  /**
   * Resolve runtime tool name for a package/subpackage before calling it directly.
   */
  resolveToolName?(request: ComposeResolveToolNameRequest): Promise<string> | string;

  h<TProps extends Record<string, unknown> = Record<string, unknown>>(
    type: string,
    props?: TProps,
    children?: ComposeChildren
  ): ComposeNode;

  Modifier: ComposeModifierProxy;

  UI: ComposeUiFactoryRegistry &
    ComposeMaterial3GeneratedUiFactoryRegistry &
    Record<string, ComposeNodeFactory<Record<string, unknown>>>;
}

export type ComposeDslScreen = (ctx: ComposeDslContext) => ComposeNode | Promise<ComposeNode>;
