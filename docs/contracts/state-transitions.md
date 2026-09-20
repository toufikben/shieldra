# Group C Event State Transitions

## States

`INITIATED`, `COLLECTING`, `READY`, `DELIVERING`, `DELIVERED`, `DEFERRED`, and `FAILED_FINAL` are the complete declared states.

## Allowed transitions

| From | Allowed next states | Rule |
|---|---|---|
| `INITIATED` | `COLLECTING`, `FAILED_FINAL` | A new event may enter collection or fail finally. |
| `COLLECTING` | `READY`, `DEFERRED`, `FAILED_FINAL` | Collection may complete, be deferred, or fail finally. |
| `READY` | `DELIVERING`, `DEFERRED`, `FAILED_FINAL` | A ready event may enter delivery, be deferred, or fail finally. |
| `DELIVERING` | `DELIVERED`, `DEFERRED`, `FAILED_FINAL` | Delivery may complete, be deferred, or fail finally. |
| `DEFERRED` | `COLLECTING`, `READY`, `DELIVERING`, `FAILED_FINAL` | Deferred work is non-terminal and may resume at a phase-appropriate point. |
| `DELIVERED` | None | Delivered is terminal. |
| `FAILED_FINAL` | None | Failed final is terminal. |

The pure `transition` function returns `Allowed` or `Rejected`. It does not mutate an event, schedule work, retry a channel, or implement Smart Queue behavior.
