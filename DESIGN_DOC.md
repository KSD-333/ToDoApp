# Antigravity Personal ToDo - Design Document

## 1. Core Philosophy

**Honest Productivity.**

Most todo apps fail because they encourage hoarding tasks. They become graveyards of good intentions, leading to guilt and avoidance. This app is built on **subtraction and clarity**.

*   **Fewer features are better:** We remove the friction of management. If managing a task app takes more time than doing the task, the app has failed.
*   **Reduced decision fatigue:** We do not show you everything at once. We show you what is relevant *now* based on your energy and time.
*   **Honesty over Optimism:** We treat a skipped task as data, not failure. If you skip a task 3 times, the app will ask if you should delete it, not shame you for it.
*   **Problem scope:** This app does NOT solve project management or team collaboration. It solves *personal execution*.

## 2. Core Components

1.  **The Task System (Actionable Items)**
    *   **Purpose:** To capture intent and convert it into a physical action.
    *   **Data:** Title, actionable step, context.
    *   **Problem Solved:** "What do I actually need to do?" vs "Vague project idea".

2.  **Energy & Time Awareness**
    *   **Purpose:** To filter tasks by reality. You cannot do deep work with "Low" energy.
    *   **Data:** `energy_level` (High/Med/Low), `time_estimate` (minutes).
    *   **Problem Solved:** prevents committing to heavy tasks when tired.

3.  **Progress Measurement (The Daily Loop)**
    *   **Purpose:** Track movement, not just completion.
    *   **Data:** Tasks completed today vs. tasks planned.
    *   **Problem Solved:** The feeling of "I worked all day but did nothing."

4.  **Reflection System (The Mirror)**
    *   **Purpose:** Daily and weekly forced pauses to clean the list.
    *   **Data:** Skipped counts, mood logs (optional/simple).
    *   **Problem Solved:** Task hoarding and list clutter.

5.  **AI Assistance (The Silent Partner)**
    *   **Purpose:** To act as a reasoning engine, not a boss.
    *   **Data:** Task patterns, complexity analysis.
    *   **Problem Solved:** Breaking down intimidating tasks and identifying procrastination patterns.

## 3. Task Model

The database schema (Room/SQLite) will focus on these attributes:

```kotlin
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,              // The core idea "Buy Groceries"
    val nextAction: String?,        // The physical start "Drive to Whole Foods"
    val estimatedMinutes: Int,      // Realistic time
    val energyLevel: EnergyLevel,   // LOW, MEDIUM, HIGH
    val priority: Priority,         // NOW, LATER, SOMEDAY
    val status: TaskStatus,         // PENDING, DONE, SKIPPED, POSTPONED
    val createdAt: Long,
    val lastModified: Long,
    val skipCount: Int = 0          // How many times was this pushed to "tomorrow"?
)
```

*   **Why Next Action?** "Plan Vacation" is impossible to "do". "Research hotels in Kyoto" is doable. The app forces this distinction.
*   **Why Energy?** Context tags like #home or #work are brittle. "I have 15 mins and low energy" is a universal human state.
*   **Why Skipped?** Tracking skips allows the app to say "You've skipped this 4 times. Is it actually important?"

**Real Example:**
> **Title:** Fix the squeaky door
> **Next Action:** Buy WD-40 from hardware store
> **Time:** 30 mins
> **Energy:** Low
> **Status:** Pending

## 4. Working Logic

**Morning Flow (The Selection):**
1.  App opens. Screen is blank except for "Good Morning".
2.  App presents yesterday's unfinished tasks + today's scheduled ones.
3.  User *must* select 3-5 tasks for "Today". Everything else is hidden in "Backlog".
4.  Intent is set.

**During the Day (The Execution):**
1.  User sees only "Today's" tasks.
2.  Focus mode: Shows only ONE task (the top priority).
3.  If a task is blocked, user hits "Postpone". AI asks for a reason (briefly) to learn.

**When Tasks are Restricted:**
1.  If creating a task like "Write Book", AI silently intercepts: "This looks big. Want to break it down?"
2.  If adding too many tasks for one day: "You have 6 hours of work logged for a 4 hour evening. Remove one?"

**End-of-Day Reflection (The Closing):**
1.  User reviews "Done" list. Visual satisfaction (subtle glow).
2.  User reviews "Leftover".
    *   Option A: Move to tomorrow.
    *   Option B: Return to backlog.
    *   Option C: Delete.
3.  App closes. "Day Complete."

**Weekly Review:**
Saturday morning prompt. Shows "Stale Tasks" (older than 2 weeks). Bulk delete or archive option.

## 5. AI Usage (ChatGroq)

AI is a tool, not a character. It has no avatar. It lives in the logic layer.

**Allowed Actions:**
*   **Breakdown:** Input "Launch Website" -> AI Suggests: "Buy Domain", "Install CMS", "Draft Homepage".
*   **Pattern Recognition:** "You often skip writing tasks on Fridays."
*   **Clarification:** "Your task 'Call Mom' has no specific time context. Is this a weekend task?"

**Example Prompt (System to AI):**
```text
Task: "Finish report"
User_Energy: Low
Current_Time: 9:00 PM

Context: User usually does creative work in mornings.

Action: Analyze feasibility.
```

**Example AI Response (JSON):**
```json
{
  "feasible": false,
  "reason": "High cognition task during low energy period.",
  "suggestion": "Outline the report structure only (5 mins)?"
}
```

## 6. UI / UX Design (Antigravity Style)

**Aesthetic:** Glassmorphism, large typography (Inter/Robot), pure black (OLED) or paper white backgrounds. No dividing lines, only whitespace.

1.  **Home (The Focus)**
    *   **Layout:** Single large card in center. "Current Focus".
    *   **Bottom Sheet:** "Up Next" (collapsed).
    *   **Interaction:** Swipe Right to Complete. Swipe Left to Skip (requires reason).
    *   **Feeling:** Tunnel vision. Calm.

2.  **Task Detail (The Context)**
    *   **Layout:** Modal popup. Blurred background.
    *   **Input:** "What is the very next physical action?" (Text field).
    *   **Energy Slider:** A subtle gradient bar (Blue=Low, Purple=Med, Orange=High).

3.  **Daily Reflection (The Mirror)**
    *   **Layout:** vertical list of "Done" items fading in.
    *   **Summary:** "You cleared 4 items. You pushed 2."
    *   **Action:** A physical "Close Day" button that clears the screen.

4.  **Weekly Review**
    *   **Layout:** A grid of "Stale Tasks".
    *   **Interaction:** Tap to keep, double-tap to delete. Fast cleanup.

**Animation:**
*   Completion isn't a checkmark; the task *dissolves* into the background.
*   New tasks *float* in from the bottom.

## 7. Example User Day

**08:00 AM:**
User opens app. 12 tasks in backlog. User picks 3: "Debug Login", "Email Client", "Call Dentist".
App calculates: ~3.5 hours work. "Looks realistic."

**11:00 AM:**
User finishes "Debug Login". Swipe right. It dissolves.
User feels tired. Updates status of "Email Client" to "Postponed".
AI Prompt: "You postponed this twice. Is the next action meaningful?"
User realizes "Email Client" is vague. Changes it to "Draft Response to specific question".

**08:00 PM:**
App Notification: "Close the day?"
User opens Reflection. "Call Dentist" was skipped.
User taps "Move to Tomorrow".
App asserts: "Day closed." Screen goes dark.

## 8. Intentional Exclusions

*   **No Streaks:** Productivity is cyclical, not linear. Streaks create anxiety.
*   **No Social Sharing:** Nobody cares about your grocery list. This is private space.
*   **No Due Dates (Soft):** We use specific days, but red meaningless "Overdue" badges are banned. A task is either relevant today or it isn't.
*   **No Complex Nesting:** 1 level of subtasks maximum. If it's deeper, it's a project, use a different tool.

## 9. Final Summary

*   **Specialty:** A todo app that actively tries to execute tasks, not storing them.
*   **Target:** Professionals who are overwhelmed by complex tools like Jira/Notion for personal life.
*   **Mechanism:** Energy-based filtering and AI-assisted decomposition.
*   **Vibe:** A quiet room in a noisy house.
*   **Differentiation:** Failure is treated as data for improvement, not a moral failing.
*   **Tech:** Local-first, fast, private, intelligent.
