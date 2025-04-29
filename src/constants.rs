use std::time::Duration;

use typedb_driver::TransactionOptions;

use crate::constants::common::SECONDS_IN_HOUR;

pub mod common {
    pub const SECONDS_IN_MINUTE: u64 = 60;
    pub const MINUTES_IN_HOUR: u64 = 60;
    pub const SECONDS_IN_HOUR: u64 = SECONDS_IN_MINUTE * MINUTES_IN_HOUR;

    pub const ERROR_QUERY_POINTER_LINES_BEFORE: usize = 2;
    pub const ERROR_QUERY_POINTER_LINES_AFTER: usize = 2;
}

pub const DEFAULT_TRANSACTION_TIMEOUT: Duration = Duration::from_secs(1 * SECONDS_IN_HOUR);
