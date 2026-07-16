# Active project objective

The active plan for this repository is to **implement compatibility between Villager Recruits 1.15.2 and the Artillery Addon / Epic Knights: Artilleries and Firearms**.

## Do not follow stale context

- Do **not** treat a previous Recruits-vs-upstream audit as the current task.
- `docs/audit/recruits-1.15.2-comparison.md` was created from an erroneous stale context and is not the implementation plan or deliverable.
- Do not spend the task auditing Recruits upstream unless the user explicitly asks for that.

## Actual work target

Implement and verify the Artillery compatibility layer: inspect the Artillery API, integrate supported weapons/ammunition/projectiles into Recruits crossbowman AI as appropriate, preserve server-side ownership and friendly-fire rules, and add focused tests/build verification. The existing Medieval Boomsticks adapter is reference architecture only; it does not complete the Artillery task.

Start by reading `docs/compat/artillery-addon-1.14.0-api.md`, the current build/dependency files, and the existing compatibility adapter/AI code. Trace actual symbols before editing. Report what is implemented versus missing. Do not claim the Artillery compatibility is complete merely because the Recruits audit or Medieval Boomsticks tests pass.

Do not commit or push unless the user explicitly requests it. Do not spawn subagents/delegates unless the user explicitly requests them.
