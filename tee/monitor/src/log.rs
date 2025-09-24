#[cfg(feature = "dbg")]
#[macro_export]
macro_rules! dbg{
    ($($arg:tt)*) => {
        { semihosting::hprintln!("[SM] {}:{} : {}", file!(), line!(), format_args!($($arg)*)) };
    };
}

#[cfg(not(feature = "dbg"))]
#[macro_export]
macro_rules! dbg {
    ($($arg:tt)*) => {};
}
