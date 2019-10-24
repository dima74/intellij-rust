# Completion:
* Python полностью делает completion через обращение к внешнему процессу
* Kotlin - ???
    - consoleView.virtualFile и consoleView.file не пересоздаются для каждой новой команды

# Syntax highlighting:
* Python --- всё работает из коробки (в python любой statement может быть на top level)
* Kotlin --- ???
    - если убрать переименовывание virtualFile в .kts, то highlighting продолжит работать
    - todo поискать: isScript


# Tries
* если в LanguageConsoleImpl передать свой LightVirtualFile с содержимым, то это содержимое станет начальным значением строки
