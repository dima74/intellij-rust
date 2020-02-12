Done * Запуск через action (удаление ToolWindow и RunnerFactory)
    - RsConsoleRunner.kt (title, убрать showConsole, checkNeedInstallEvcxr, убрать newline)
    - RsConsoleRunnerFactory.kt
    - RsConsoleToolWindow.kt
    - RsConsoleToolWindowFactory.kt
    - RunRustConsoleAction.kt
Done * Обновление evcxr (добавление команды в контекст только при успешном выполнени: Communication, Context, ExecuteActionHandler)
    - RsConsoleCodeFragmentContext.kt (кроме variablesFile)
    - ConsoleView: addToContext
    - RsConsoleCommunication.kt
    - RsConsoleExecuteActionHandler.kt
    - Cargo
    - Evcxr
Open * Actions
    - RsConsoleActions.kt (кроме ShowVarsAction)
    - RsConsoleRunner.kt (createContentDescriptorAndActions, fillRunActionsToolbar, fillOutputActionsToolbar)

* История команд (удаление CommandHistory и HistoryListener, вместо них используем стандартный, добавление <consoleHistoryModelProvider> и <scratch.rootType>)
    - CommandHistory.kt
    - HistoryKeyListener.kt
    - RsConsoleHistoryModelProvider.kt
    - RsConsoleRootType.kt
    - RsConsoleRunner.kt (createConsoleView, createExecuteActionHandler)


Open * Тесты. После "Обновление evcxr"
* Variables view (<projectService>). После "Обновление evcxr"
    - RsStmt
    - variablesFile в Context
    - RsConsoleOptions.kt
    - RsConsoleVariablesView.kt
    - RsConsoleView (initVariablesWindow)
    - RsConsoleRunner (initVariablesWindow в connect)
    - presentation/Utils
    - RsStructureViewModel.kt
    - RsConsoleActions.kt (только ShowVarsAction)
