//! A simplified OnceCell implementation
//!
//! This OnceCell does not support lazy evaluation with a macro,
//! yet it only guarantees that inner data cannot be modified once after being set().
//!
//! # Concurrency-safety
//!
//! One straightforward way to make `set()` completely concurrency-safe is
//! to use atomic feature for inner like, `inner: AtomicCell<Option<T>>`.
//! Alternative way for it is to enforce `set()` to be called in `new()`
//! for another object creation which contains OnceCell as a member.
//!
//! ```ignore
//! struct AAA { ... }
//! impl AAA {
//!    pub fn new(p: usize) {
//!       // this new() function is thread-safe, because
//!       // a newly created object is not yet visible from a global view. (only local-thread can see this object)
//!       let a = OnceCell::new(...);
//!       a.set(p);
//!       AAA { a }
//!    }
//! }
//! ```

use core::cell::UnsafeCell;

pub struct OnceCell<T> {
    // Invariant: written to at most once.
    inner: UnsafeCell<Option<T>>,
}

impl<T> OnceCell<T> {
    /// Creates a new empty cell.
    pub const fn new() -> OnceCell<T> {
        OnceCell {
            inner: UnsafeCell::new(None),
        }
    }

    /// Gets a reference to the underlying value.
    /// Returns `None` if the cell is empty.
    pub fn get(&self) -> Option<&T> {
        // Safe due to `inner`'s invariant
        unsafe { &*self.inner.get() }.as_ref()
    }

    /// Sets the contents of this cell to `value`.
    pub fn set(&self, value: T) {
        let slot = unsafe { &mut *self.inner.get() };
        if slot.is_none() {
            *slot = Some(value);
        }
    }
}
