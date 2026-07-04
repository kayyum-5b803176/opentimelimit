/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.extensions.MinuteOfDay

object DatabaseMigrations {
    val MIGRATE_TO_V2 = object: Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user` ADD COLUMN `category_for_not_assigned_apps` TEXT NOT NULL DEFAULT \"\"")
        }
    }

    val MIGRATE_TO_V3 = object: Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `category` ADD COLUMN `parent_category_id` TEXT NOT NULL DEFAULT \"\"")
        }
    }

    val MIGRATE_TO_V4 = object: Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `device` ADD COLUMN `did_reboot` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `device` ADD COLUMN `consider_reboot_manipulation` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V5 = object: Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // device table
            db.execSQL("ALTER TABLE `device` ADD COLUMN `current_overlay_permission` TEXT NOT NULL DEFAULT \"not granted\"")
            db.execSQL("ALTER TABLE `device` ADD COLUMN `highest_overlay_permission` TEXT NOT NULL DEFAULT \"not granted\"")
            db.execSQL("ALTER TABLE `device` ADD COLUMN `current_accessibility_service_permission` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `device` ADD COLUMN `was_accessibility_service_permission` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `device` ADD COLUMN `enable_activity_level_blocking` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `device` ADD COLUMN `q_or_later` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `device` ADD COLUMN `default_user` TEXT NOT NULL DEFAULT \"\"")
            db.execSQL("ALTER TABLE `device` ADD COLUMN `default_user_timeout` INTEGER NOT NULL DEFAULT 0")

            // category table
            db.execSQL("ALTER TABLE `category` ADD COLUMN `block_all_notifications` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `category` ADD COLUMN `time_warnings` INTEGER NOT NULL DEFAULT 0")

            // app_activity table
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_activity` (`device_id` TEXT NOT NULL, `app_package_name` TEXT NOT NULL, `activity_class_name` TEXT NOT NULL, `activity_title` TEXT NOT NULL, PRIMARY KEY(`device_id`, `app_package_name`, `activity_class_name`))")

            // allowed_contact table
            db.execSQL("CREATE TABLE IF NOT EXISTS `allowed_contact` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `phone` TEXT NOT NULL)")
        }
    }

    val MIGRATE_TO_V6 = object: Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `device` ADD COLUMN `had_manipulation_flags` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V7 = object: Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user` ADD COLUMN `blocked_times` TEXT NOT NULL DEFAULT \"\"")
        }
    }

    val MIGRATE_TO_V8 = object: Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `category` ADD COLUMN `min_battery_charging` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `category` ADD COLUMN `min_battery_mobile` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V9 = object: Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `category` ADD COLUMN `temporarily_blocked_end_time` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V10 = object: Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `category` ADD COLUMN `sort` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V11 = object: Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // this is empty
            //
            // a new possible enum value was added, the version upgrade enables the downgrade mechanism
        }
    }

    val MIGRATE_TO_V12 = object: Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `category` ADD COLUMN `extra_time_day` INTEGER NOT NULL DEFAULT -1")
        }
    }

    val MIGRATE_TO_V13 = object: Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `user_key` (`user_id` TEXT NOT NULL, `key` BLOB NOT NULL, `last_use` INTEGER NOT NULL, PRIMARY KEY(`user_id`), FOREIGN KEY(`user_id`) REFERENCES `user`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_key_key` ON `user_key` (`key`)")
        }
    }

    val MIGRATE_TO_V14 = object: Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `time_limit_rule` ADD COLUMN `start_minute_of_day` INTEGER NOT NULL DEFAULT ${TimeLimitRule.MIN_START_MINUTE}")
            db.execSQL("ALTER TABLE `time_limit_rule` ADD COLUMN `end_minute_of_day` INTEGER NOT NULL DEFAULT ${TimeLimitRule.MAX_END_MINUTE}")
            db.execSQL("ALTER TABLE `time_limit_rule` ADD COLUMN `session_duration_milliseconds` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `time_limit_rule` ADD COLUMN `session_pause_milliseconds` INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE `used_time` RENAME TO `used_time_old`")
            db.execSQL("CREATE TABLE IF NOT EXISTS `used_time` (`day_of_epoch` INTEGER NOT NULL, `used_time` INTEGER NOT NULL, `category_id` TEXT NOT NULL, `start_time_of_day` INTEGER NOT NULL, `end_time_of_day` INTEGER NOT NULL, PRIMARY KEY(`category_id`, `day_of_epoch`, `start_time_of_day`, `end_time_of_day`))")
            db.execSQL("INSERT INTO `used_time` SELECT `day_of_epoch`, `used_time`, `category_id`, ${MinuteOfDay.MIN} AS `start_time_of_day`, ${MinuteOfDay.MAX} AS `end_time_of_day` FROM `used_time_old`")
            db.execSQL("DROP TABLE `used_time_old`")

            db.execSQL("CREATE TABLE IF NOT EXISTS `session_duration` (`category_id` TEXT NOT NULL, `max_session_duration` INTEGER NOT NULL, `session_pause_duration` INTEGER NOT NULL, `start_minute_of_day` INTEGER NOT NULL, `end_minute_of_day` INTEGER NOT NULL, `last_usage` INTEGER NOT NULL, `last_session_duration` INTEGER NOT NULL, PRIMARY KEY(`category_id`, `max_session_duration`, `session_pause_duration`, `start_minute_of_day`, `end_minute_of_day`), FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `session_duration_index_category_id` ON `session_duration` (`category_id`)")
        }
    }

    val MIGRATE_TO_V15 = object: Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user` ADD COLUMN `flags` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V16 = object: Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `user_limit_login_category` (`user_id` TEXT NOT NULL, `category_id` TEXT NOT NULL, PRIMARY KEY(`user_id`), FOREIGN KEY(`user_id`) REFERENCES `user`(`id`) ON UPDATE CASCADE ON DELETE CASCADE , FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `user_limit_login_category_index_category_id` ON `user_limit_login_category` (`category_id`)")
        }
    }

    val MIGRATE_TO_V17 = object: Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `category_network_id` (`category_id` TEXT NOT NULL, `network_item_id` TEXT NOT NULL, `hashed_network_id` TEXT NOT NULL, PRIMARY KEY(`category_id`, `network_item_id`), FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
        }
    }

    val MIGRATE_TO_V18 = object: Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `category` ADD COLUMN `disable_limits_until` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V19 = object: Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `child_task` (`task_id` TEXT NOT NULL, `category_id` TEXT NOT NULL, `task_title` TEXT NOT NULL, `extra_time_duration` INTEGER NOT NULL, `pending_request` INTEGER NOT NULL, `last_grant_timestamp` INTEGER NOT NULL, PRIMARY KEY(`task_id`), FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
        }
    }

    val MIGRATE_TO_V20 = object: Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `time_limit_rule` ADD COLUMN `per_day` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V21 = object: Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_limit_login_category` ADD COLUMN pre_block_duration INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V22 = object: Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `category` ADD COLUMN `flags` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V23 = object: Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE category ADD COLUMN block_notification_delay INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V24 = object: Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // nothing to do, there was just a new config item type added
        }
    }

    val MIGRATE_TO_V25 = object: Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `category_time_warning` (`category_id` TEXT NOT NULL, `minutes` INTEGER NOT NULL, PRIMARY KEY(`category_id`, `minutes`), FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
        }
    }

    val MIGRATE_TO_V26 = object: Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE device ADD COLUMN manipulation_flags INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V27 = object: Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `user_u2f_key` (`key_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, `key_handle` BLOB NOT NULL, `public_key` BLOB NOT NULL, `next_counter` INTEGER NOT NULL, FOREIGN KEY(`user_id`) REFERENCES `user`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_u2f_key_user_id` ON `user_u2f_key` (`user_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_u2f_key_key_handle_public_key` ON `user_u2f_key` (`key_handle`, `public_key`)")
        }
    }

    val MIGRATE_TO_V28 = object: Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `widget_category` (`widget_id` INTEGER NOT NULL, `category_id` TEXT NOT NULL, PRIMARY KEY(`widget_id`, `category_id`), FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_widget_category_category_id` ON `widget_category` (`category_id`)")
        }
    }

    val MIGRATE_TO_V29 = object: Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `widget_config` (`widget_id` INTEGER NOT NULL, `translucent` INTEGER NOT NULL, PRIMARY KEY(`widget_id`))")
        }
    }
}