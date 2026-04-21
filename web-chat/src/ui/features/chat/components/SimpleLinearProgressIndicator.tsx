export function SimpleLinearProgressIndicator({
  progress
}: {
  progress: number;
}) {
  const normalizedProgress = Math.max(0, Math.min(1, progress));

  return (
    <div className="simple-linear-progress">
      <div className="simple-linear-progress-bar" style={{ width: `${normalizedProgress * 100}%` }} />
    </div>
  );
}
