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
    entries: Option<Vec<String>>,
}

impl CompletionCache {
    pub(crate) fn new() -> Self {
        Self { entries: None }
    }
}

pub(crate) fn database_name_completer_fn(
    driver: Arc<TypeDBDriver>,
    runtime: BackgroundRuntime,
    cache: Arc<Mutex<CompletionCache>>,
) -> Box<InputCompleterFn> {
    // we have to do an annoying hack to let auto-complete function with a live database connection...
    Box::new(move |input| {
        let should_fetch = {
            let cache = cache.lock().unwrap();
            input.len() <= 1 || cache.entries.is_none()
        };
        if should_fetch {
            let driver = driver.clone();
            let runtime = runtime.clone();
            match runtime.run(async move { driver.databases().all().await }) {
                Ok(dbs) => {
                    let entries: Vec<String> = dbs.iter().map(|db| db.name().to_owned()).collect();
                    let filtered = entries.iter().filter(|e| e.starts_with(input)).cloned().collect();
                    cache.lock().unwrap().entries = Some(entries);
                    filtered
                }
                Err(_) => Vec::new(),
            }
        } else {
            let cache = cache.lock().unwrap();
            cache
                .entries
                .as_ref()
                .map_or_else(Vec::new, |entries| entries.iter().filter(|e| e.starts_with(input)).cloned().collect())
        }
    })
}

pub(crate) fn user_name_completer_fn(
    driver: Arc<TypeDBDriver>,
    runtime: BackgroundRuntime,
    cache: Arc<Mutex<CompletionCache>>,
) -> Box<InputCompleterFn> {
    Box::new(move |input| {
        let should_fetch = {
            let cache = cache.lock().unwrap();
            input.len() <= 1 || cache.entries.is_none()
        };
        if should_fetch {
            let driver = driver.clone();
            let runtime = runtime.clone();
            match runtime.run(async move { driver.users().all().await }) {
                Ok(users) => {
                    let entries: Vec<String> = users.iter().map(|u| u.name().to_owned()).collect();
                    let filtered = entries.iter().filter(|e| e.starts_with(input)).cloned().collect();
                    cache.lock().unwrap().entries = Some(entries);
                    filtered
                }
                Err(_) => Vec::new(),
            }
        } else {
            let cache = cache.lock().unwrap();
            cache
                .entries
                .as_ref()
                .map_or_else(Vec::new, |entries| entries.iter().filter(|e| e.starts_with(input)).cloned().collect())
        }
    })
}

pub(crate) fn file_completer(input: &str) -> Vec<String> {
    match glob(&format!("{}*", input)) {
        Ok(paths) => paths.filter_map(Result::ok).map(|path| path.to_string_lossy().into_owned()).collect(),
        Err(_) => Vec::new(),
    }
}
