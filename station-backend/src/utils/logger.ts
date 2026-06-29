const LOG_LEVELS = { debug: 0, info: 1, warn: 2, error: 3 } as const;
type LogLevel = keyof typeof LOG_LEVELS;

const currentLevel: LogLevel = (process.env.LOG_LEVEL as LogLevel) || 'info';

export class Logger {
  private prefix: string;

  constructor(name: string) {
    this.prefix = `[${name}]`;
  }

  private log(level: LogLevel, msg: string, ...args: unknown[]) {
    if (LOG_LEVELS[level] < LOG_LEVELS[currentLevel]) return;
    const timestamp = new Date().toISOString();
    const formatted = args.length > 0 ? msg.replace(/%[Osd]/g, () => String(args.shift())) : msg;
    const line = `${timestamp} ${this.prefix} [${level.toUpperCase()}] ${formatted}`;
    switch (level) {
      case 'error': console.error(line); break;
      case 'warn':  console.warn(line);  break;
      default:      console.log(line);   break;
    }
  }

  debug = (msg: string, ...args: unknown[]) => this.log('debug', msg, ...args);
  info  = (msg: string, ...args: unknown[]) => this.log('info', msg, ...args);
  warn  = (msg: string, ...args: unknown[]) => this.log('warn', msg, ...args);
  error = (msg: string, ...args: unknown[]) => this.log('error', msg, ...args);
}
