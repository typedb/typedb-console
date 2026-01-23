/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::sync::{Arc, Mutex};

use glob::glob;
use typedb_driver::TypeDBDriver;

use crate::{repl::command::InputCompleterFn, BackgroundRuntime};

pub(crate) struct CompletionCache {
    entries: Vec<String>,
    fetch_fn: Box<dyn Fn() -> Option<Vec<String>> + Send>,
}

impl CompletionCache {
    fn update(&mut self) {
        self.entries = (self.fetch_fn)().unwrap_or_default();
    }

    fn get(&self, input: &str) -> Vec<String> {
        self.entries.iter().filter(|e| e.starts_with(input)).cloned().collect()
    }
}

pub(crate) fn new_database_cache(driver: Arc<TypeDBDriver>, runtime: BackgroundRuntime) -> Arc<Mutex<CompletionCache>> {
    Arc::new(Mutex::new(CompletionCache {
        entries: Vec::default(),
        fetch_fn: Box::new(move || {
            let driver = driver.clone();
            runtime
                .clone()
                .run(async move { driver.databases().all().await })
                .ok()
                .map(|dbs| dbs.iter().map(|db| db.name().to_owned()).collect())
        }),
    }))
}

pub(crate) fn new_user_cache(driver: Arc<TypeDBDriver>, runtime: BackgroundRuntime) -> Arc<Mutex<CompletionCache>> {
    Arc::new(Mutex::new(CompletionCache {
        entries: Vec::default(),
        fetch_fn: Box::new(move || {
            let driver = driver.clone();
            runtime
                .clone()
                .run(async move { driver.users().all().await })
                .ok()
                .map(|users| users.iter().map(|u| u.name().to_owned()).collect())
        }),
    }))
}

pub(crate) fn cached_completer(cache: Arc<Mutex<CompletionCache>>) -> Box<InputCompleterFn> {
    Box::new(move |input| {
        let mut cache = cache.lock().unwrap();
        if input.len() == 1 {
            cache.update();
        }
        cache.get(input)
    })
}

pub(crate) fn file_completer(input: &str) -> Vec<String> {
    match glob(&format!("{}*", input)) {
        Ok(paths) => paths.filter_map(Result::ok).map(|path| path.to_string_lossy().into_owned()).collect(),
        Err(_) => Vec::new(),
    }
}
