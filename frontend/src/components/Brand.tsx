export function BrandMark({ size = 22 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <path fill="#F4B740" d="M4 7h24v6a3 3 0 0 0 0 6v6H4v-6a3 3 0 0 0 0-6z" />
      <path stroke="#16202A" strokeWidth="1.6" strokeDasharray="2 2" d="M12 8v16" />
      <circle cx="19" cy="16" r="2.2" fill="#16202A" />
    </svg>
  )
}
