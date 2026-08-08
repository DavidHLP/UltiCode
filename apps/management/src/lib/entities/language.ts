export function getLanguageColor(lang: string): string {
  const colors: Record<string, string> = {
    python: 'bg-blue-500/10 text-blue-600 border-blue-500/20 hover:bg-blue-500/20',
    javascript: 'bg-yellow-500/10 text-yellow-600 border-yellow-500/20 hover:bg-yellow-500/20',
    typescript: 'bg-blue-600/10 text-blue-700 border-blue-600/20 hover:bg-blue-600/20',
    java: 'bg-orange-500/10 text-orange-600 border-orange-500/20 hover:bg-orange-500/20',
    cpp: 'bg-blue-400/10 text-blue-500 border-blue-400/20 hover:bg-blue-400/20',
    c: 'bg-gray-500/10 text-gray-600 border-gray-500/20 hover:bg-gray-500/20',
    csharp: 'bg-purple-500/10 text-purple-600 border-purple-500/20 hover:bg-purple-500/20',
    go: 'bg-cyan-500/10 text-cyan-600 border-cyan-500/20 hover:bg-cyan-500/20',
    rust: 'bg-orange-600/10 text-orange-700 border-orange-600/20 hover:bg-orange-600/20',
    ruby: 'bg-red-500/10 text-red-600 border-red-500/20 hover:bg-red-500/20',
    php: 'bg-indigo-500/10 text-indigo-600 border-indigo-500/20 hover:bg-indigo-500/20',
    swift: 'bg-orange-500/10 text-orange-600 border-orange-500/20 hover:bg-orange-500/20',
    kotlin: 'bg-purple-600/10 text-purple-700 border-purple-600/20 hover:bg-purple-600/20',
    scala: 'bg-red-600/10 text-red-700 border-red-600/20 hover:bg-red-600/20',
  }
  return colors[lang.toLowerCase()] || 'bg-muted text-muted-foreground'
}
