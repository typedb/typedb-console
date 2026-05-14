/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{future::Future, sync::Arc};

use tokio::{runtime::Runtime, sync::oneshot::channel};

#[derive(Clone)]
pub(crate) struct BackgroundRuntime {
    runtime: Arc<Runtime>,
}

impl BackgroundRuntime {
    pub(crate) fn new() -> Self {
        Self { runtime: Arc::new(Runtime::new().expect("Failed to create tokio runtime.")) }
    }

    pub(crate) fn run<F>(&self, future: F) -> F::Output
    where
        F: Future + Send + 'static,
        F::Output: Send + 'static,
    {
        let (response_sink, response) = channel();
        self.runtime.spawn(async move {
            response_sink.send(future.await).ok();
        });
        response.blocking_recv().unwrap()
    }
}
