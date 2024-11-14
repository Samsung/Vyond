use core::cell::UnsafeCell;
use core::hint::spin_loop;
use core::marker::PhantomData;
use core::ops::{Deref, DerefMut};
use core::sync::atomic::{AtomicBool, Ordering};

pub enum Error {
    Undefined,
}

pub struct RawSpinLock {
    inner: AtomicBool,
}

impl RawSpinLock {
    pub const fn new() -> Self {
        Self {
            inner: AtomicBool::new(false),
        }
    }

    pub fn lock(&self) {
        while self
            .inner
            .compare_exchange(false, true, Ordering::Acquire, Ordering::Relaxed)
            .is_err()
        {
            spin_loop();
        }
    }

    pub fn try_lock(&self) -> bool {
        !self.inner.swap(true, Ordering::Acquire)
    }

    pub fn unlock(&self) {
        self.inner.store(false, Ordering::Release);
    }
}

pub struct SpinLock<T: ?Sized> {
    lock: RawSpinLock,
    data: UnsafeCell<T>,
}

unsafe impl<T: ?Sized> Send for SpinLock<T> {}
unsafe impl<T: ?Sized> Sync for SpinLock<T> {}

impl<T> SpinLock<T> {
    /// Creates a new spinlock wrapping the supplied data
    pub const fn new(data: T) -> Self {
        Self {
            lock: RawSpinLock::new(),
            data: UnsafeCell::new(data),
        }
    }

    /// Consumes this SpinLock, returning the underlying data
    #[inline]
    pub fn into_inner(self) -> T {
        self.data.into_inner()
    }
}

impl<T: ?Sized> SpinLock<T> {
    pub fn lock(&self) -> SpinLockGuard<'_, T> {
        self.lock.lock();
        SpinLockGuard {
            lock: self,
            _marker: PhantomData,
        }
    }

    pub fn try_lock(&self) -> Result<SpinLockGuard<'_, T>, Error> {
        if self.lock.try_lock() {
            Ok(SpinLockGuard {
                lock: self,
                _marker: PhantomData,
            })
        } else {
            Err(Error::Undefined)
        }
    }

    pub unsafe fn get(&self) -> &T {
        &*self.data.get()
    }

    pub fn get_mut(&mut self) -> &mut T {
        unsafe { &mut *self.data.get() }
    }
}

pub struct SpinLockGuard<'a, T: ?Sized> {
    lock: &'a SpinLock<T>,
    _marker: PhantomData<*const ()>, // !Send + !Sync
}

impl<'a, T: ?Sized> Drop for SpinLockGuard<'a, T> {
    fn drop(&mut self) {
        self.lock.lock.unlock();
    }
}

impl<'a, T: ?Sized> Deref for SpinLockGuard<'a, T> {
    type Target = T;

    fn deref(&self) -> &Self::Target {
        unsafe { &*self.lock.data.get() }
    }
}

impl<'a, T: ?Sized> DerefMut for SpinLockGuard<'a, T> {
    fn deref_mut(&mut self) -> &mut Self::Target {
        unsafe { &mut *self.lock.data.get() }
    }
}
