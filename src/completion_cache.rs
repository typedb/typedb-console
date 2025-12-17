use std::collections::HashSet;
use std::hash::Hash;
use std::sync::{Arc, RwLock};

#[derive(Clone)]
pub struct CompletionCache<T> {
    inner: Arc<RwLock<HashSet<T>>>,
}

impl<T> CompletionCache<T>
where
    T: Eq + Hash + Clone,
{
    pub fn new() -> Self {
        Self { inner: Arc::new(RwLock::new(HashSet::new())) }
    }

    pub fn replace_all<I>(&self, items: impl IntoIterator<Item = T>) {
        *self.inner.write().unwrap() = items.into_iter().collect();
    }

    pub fn snapshot(&self) -> Vec<T> {
        self.inner.read().unwrap().iter().cloned().collect()
    }

    pub fn is_empty(&self) -> bool {
        self.inner.read().unwrap().is_empty()
    }

    pub fn clear(&self) {
        self.inner.write().unwrap().clear();
    }
}
