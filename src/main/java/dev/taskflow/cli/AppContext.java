package dev.taskflow.cli;

import dev.taskflow.application.ReminderService;
import dev.taskflow.application.TaskService;
import java.time.Clock;

/** The wired-up object graph for one invocation. Built lazily by the composition root. */
record AppContext(TaskService tasks, ReminderService reminders, Clock clock) {}
