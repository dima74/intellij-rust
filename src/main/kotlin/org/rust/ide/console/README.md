
# Completion:
* Python полностью делает completion через обращение к внешнему процессу
* Kotlin - ???
    - consoleView.virtualFile и consoleView.file не пересоздаются для каждой новой команды
* Rust
    - если в качестве context указать RsPsiFactory(project).createFunction("fn main() { let fff1 = 1; }").block!!, то будет autocomplete на fff1

# Syntax highlighting:
* Python --- всё работает из коробки (в python любой statement может быть на top level)
* Kotlin --- ???
    - если убрать переименовывание virtualFile в .kts, то highlighting продолжит работать
    - todo поискать: isScript


# Tries
* если в LanguageConsoleImpl передать свой LightVirtualFile с содержимым, то это содержимое станет начальным значением строки

## CloseAction
new CloseAction(defaultExecutor, descriptor, project) --- не работает (и в python тоже)




# Completion
* single line, multiple statements, only names
    no: repl
* single line, multiple statements in if-expr, only names
    yes: repl, expr
* single line, multiple statements in if-expr, types
    no: repl, (!)expr
* multi line, only names
    yes: repl, expr
* multi line, types
    yes: repl, expr
* multi line, methods of types
    yes: repl expr
* macros
    no: repl, expr
