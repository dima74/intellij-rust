# Алгоритм

- Обрабатываем крейты по отдельности
- Для каждого крейта
    - Строим дерево модулей
    - Для каждого модуля хотим получить словарь всех доступных айтемов этого модуля
        - Map<String, ItemLocation>
            - ключи - имя айтема + namespace
            - значения - путь к модулю в котором объявлен этот айтем
        - Сначала заполняем map'ы явно объявленными айтемами
        - Заводим список импортов для обработки
        - Fixed point iteration: резолвим импорты пока что-то меняется

## Fixed point iteration

В rust-analyzer есть три списка импортов:

* unresolved_imports - такие `use path;`/`use path::*;` что мы ещё не знаем куда резолвится path
    - как только зарезолвили path, перемещаем импорт в glob_imports или resolved_imports и больше не трогаем
* glob_imports - для каждого `mod` храним такие `use path::*;` что мы уже знаем куда резолвится `path`
  и `path.resolve() == mod`
    - когда добавляем новый айтем в scope `mod`, обновляем также scope модулей из которых есть глоб-импорты на `mod`
    - по сути обратный граф глоб-импортов
* resolved_imports
    - нужны для макросов?

## Алгоритм подробно

1. Создаём дерево модулей
2. Заполняем defMap явно объявленными айтемами
3. Находим все импорты и преобразуем их в plain data class
4. Fixed point iteration
    - ...

# Вопросы

# Todo

- добавление приватных айтемов: проверить есть ли в rust-analyzer предупреждение о private visibility
- мб для обычных импортов тоже использовать подход с графом? потому что вроде модули почти всегда объявляются явно
- primitive types & shadowing
  https://github.com/rust-lang/rfcs/blob/master/text/2700-associated-constants-on-ints.md
  ```rust
  use std::u32;
  assert_eq!(u32::MAX, u32::max_value());
  ```
- Энергичное построение CrateDefMap, потому что резолв нужен почти для всего Нужно будет аккуратно сделать cancellation

# Ленивое построение DefMap

- Для каждого crateId храним флажок - "строится ли прямо сейчас DefMap этого крейта"
- Построение DefMap полностью оборачиваем в read action и updateAllDefMaps тоже (но надо добавить debounce)
- Хотим получить DefMap для крейта:
    - Если DefMap сейчас строится, то она гарантированно достроится => просто ждём её завершения Вместо флажка можем
      хранить nullable `CompletableFeature` ?
    - Иначе проверяем нужно ли обновить DefMap и если да, то сами строим DefMap

Три типа действий:

- psi изменился - сохраняем файл, планируем запуск task с макросами
- task с макросами - на основе сохранённых файлов считаем какие крейты изменились
- getDefMap - ...

# $crate

Насколько я понимаю должен быть примерно такой алгоритм: В Macro и MacroCall (новые дата классы) поддерживаем мапку из
позиции в тексте (на которой начинается IntellijRustDollarCrate) в CrateId. Когда раскрываем macro call, хотим в
полученном expandedText для каждого вхождения IntellijRustDollarCrate найти CrateId. Возможны три варианта, откуда
пришёл IntellijRustDollarCrate:

- $crate из самого макроса (macro_rules) - знаем нужный crateId
- IntellijRustDollarCrate из самого макроса (macro_rules) - используем мапку из Macro
- IntellijRustDollarCrate из macro call - используем мапку из MacroCall

# Инкрементальность

- Есть два типа событий - знаем какой файл изменился или не знаем
- Поддерживаем внутри DefMap modification stamp для каждого файла ⇒ если не знаем какой файл изменился, можем найти
    - Проблема: был unresolved mod declaration, пользователь создал новый файл - мы это не обнаружим Решение: также
      храним список всех файлов, которые сейчас не существуют, но могут повлиять на резолв
- Приходит событие об изменении файла - нужно сохранить куда-нибудь fileId
    - Сейчас устанавливается флажок, что нужно перераскрыть макросы, после завершения write action запускается
      MacroExpansionTask
- Нужно аккуратно обработать список изменённых файлов в ExpansionTask
    - Чтобы файлы не потерялись если таска была отменена
    - Файлы могут быть из разных крейтов
- Нужно пересчитать все defMap зависящие от изменённых defMap
    - Нужно хранить time последнего обновления defMap? Для поддержки первоначальной верификации всех DefMap при запуске
      IDE
    - Идея: считаем хеш публичного интерфейса крейта, если он не изменился, то можно не пересчитывать зависящие крейты
- Хеш
    - Есть MessageDigest
    - Cargo features, Dependencies, Edition - при изменении этого нужно пересчитать defMap
        - Храним хеш от этого всего в DefMap ?
    - Хеш для файла - переиспользуем ModCollector, чтобы не было дублирования кода
        - Импорты (class Import)
        - Macro call (class MacroCallInfo)
        - Items (class PerNs)

# Замечания

- В rust-analyzer импорт считается resolved если он стал resolved хотя бы в одном namespace (for performance reason)
  https://github.com/rust-analyzer/rust-analyzer/blob/d47834ee1b8fce7956272e27c9403021940fec8e/crates/ra_hir_def/src/nameres/collector.rs#L394
- В rust-analyzer `use` и `extern crate` обрабатываются единообразно, и ещё оказывается может быть `pub extern crate`
- Можно импортировать enum variant
- enum variant одновременно находится и в types и в values ?
  https://github.com/rust-analyzer/rust-analyzer/blob/ecac5d7de2192873c24b7b06d4964d188d8abe6a/crates/ra_hir_def/src/nameres/path_resolution.rs#L229
- В rust-analyzer чтобы не было циклических ссылок хранятся индекс объекта в массиве
- Мы для каждого модуля храним Map<String, VisItem>, где VisItem содержит строку - абсолютный путь к айтему, а в
  rust-analyzer хранится Map<String, ModuleDefIf>, где ModuleDefIf это id объекта (модуль/структура/...)
- Если наш резолв заменить на `return false`, то проходят 269 тестов
- В одном PerNs могут быть VisItem из разных крейтов (например `use crate1::*; use crate2::*`)
- В rust-analyzer в ModData хранится `unresolved: List<String>` - наверно нам тоже нужен? (для completion и т.д.)
- Хотим чтобы импорты приватных айтемов учитывались при резолве (ide должна работать с некорректным кодом)
  Например:

```rust
mod foo {
    fn func() {}
}

fn main() {
    use foo::func;
    func();
    //^ resolves correctly
}
```

В rust-analyzer, похоже, глоб-импорты импортируют только публичное что-нибудь, а для обычных импортов видимость не
проверяется

- Как резолвятся пути: https://github.com/rust-lang/rust/pull/58349
    * 2018 edition
        - `::path` - только в extern prelude
        - `path` - uniform (?) (в локальном scope если есть, иначе в extern prelude)
    * 2015 edition
        - in imports and `::path`
            - Crate-relative with fallback to extern prelude
        - `path` - только в локальном scope
- Использовать Visibility.Invalid при обработке импортов на приватные айтемы?

## Visibility & Multiresolve & Cfg

Если нет cfg, возможные состояния VisItem в одной namespace:

- Empty
- Один/несколько invisible items
- Один/несколько visible items
    - Visible item overrides все invisible items

Если есть cfg:

- Могут быть несколько модулей с одним именем (`#[path]`), но с разными cfg


- Внутри cfg-enabled - честно проверяем cfg
- Внутри cfg-disabled - игнорируем cfg (не получится ли большой оверхед?)

Можем override invisible items только если они оба cfg-enabled или оба cfg-disabled (?)

//- для cfg-disabled модулей считаем что объявление модуля имеет особенную visibility, // тогда мы не будем учитывать
его при резолве, но в нём самом резолв будет работать

Use cases:

- From correct cfg-enabled code - честно проверяем visibility и cfg
- From cfg-disabled code - честно проверяем visibility, но игнорируем cfg
- Во время completion - игнорируем visibility

## Макросы

- # [macro_export] добавляет макрос в scope crateRoot
- `#[macro_use] mod foo;` добавляет все legacy макросы из `foo` в текущий scope
  "все legacy" - то есть `macro2` в `pub use macro1 as macro2;` не добавится

# План

1. `List<Crate>` -> `Map<CrateId, CrateDefMap>`
2. Macros 2.1 Include macros. resolve. 2.2 Other macros. MacroExpander.
3. Incremental. Hashing.
4. Performance

Rust-Analyzer:

- nameres/collector.rs - collect_defs
- CrateDefMap

Rustc:

- ImportResolver::resolve_imports

```rust
extern crate foobar;

use foobar::bar;

// expandedItemsExceptImpl
mod foo {
    // foo:
    fn foo1() {}

    //   - value foo1       => CrateId::foo
    pub fn foo2() {}

    //   - pub value foo2   => CrateId::foo
    pub struct Quux;        //   - pub type Quux    => CrateId::foo
}

use foo::foo1;

// crate::foo
mod bar {
    // bar:
    pub use crate::foo::*;  //  - pub value foo2    => CrateId::foo
    //  - pub type Quux     => CrateId::foo
    //  - pub type Quuuuux  => CrateId::foo
}

// crate::baz
mod baz {
    // baz:
    use crate::bar::*;      //  - value foo2        => CrateId::foo
    //  - type Quux         => CrateId::foo
    //  - type Quuuuux      => CrateId::foo
}

use foo::*;

fn asdasd() {
    fn asdd() {}
    {
        let b1 = 123;
        let b2 = 123;
        let b4 = 123;
        let b5 = 123;
        let a = bar1;
    }
}
// Map<RsMod, Map<Pair<Namespace, String>, PsiElement>>
// 1. `List<Crate>` => Map<CrateId, CrateDefMap>
// 2. Macros
//   2.1 Include macros. resolve.
//   2.2 Other macros. MacroExpander.
// 3. Incremental. Hashing.
// 4. Performance
// psi.getStub()
// psi.getGreenStub() ?: StubTreeLoader.readOrBuild(psi.project, psi.virtualFile, psi)
// Stub
```

## Unused imports intention
В PerNsHashMap для каждого VisItem храним бит - использовался ли данный VisItem (существует ли импорт на данный item)
В resolvePathFp мы вызываем ModData.getVisibleItem - вот здесь можно устанавливать наш флажок

Далее, у нас есть импорт, мы делаем resolve на него, получаем List<RsNamedElement>
Нужно по RsNamedElement получить VisItem в scope модуля (видимо можно по crateRelativePath просто матчить)
Импорт used (by other imports) <=> хотя бы в одном VisItem флажок установлен
