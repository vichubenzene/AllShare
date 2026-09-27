export function StateCard({ title, body }: { title: string; body?: string }) {
  return (
    <section className="rounded-2xl border border-zinc-200 bg-white px-6 py-14 text-center shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      <h1 className="text-lg font-semibold tracking-tight">{title}</h1>
      {body ? <p className="mx-auto mt-2 max-w-sm text-sm text-zinc-500 dark:text-zinc-400">{body}</p> : null}
    </section>
  );
}

export function LoadingCard() {
  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      <div className="h-4 w-28 animate-pulse rounded bg-zinc-200 dark:bg-zinc-800" />
      <div className="mt-6 h-28 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />
      <div className="mt-6 h-4 w-36 animate-pulse rounded bg-zinc-200 dark:bg-zinc-800" />
    </section>
  );
}
