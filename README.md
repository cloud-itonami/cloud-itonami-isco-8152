# cloud-itonami-isco-8152

Open Occupation Blueprint for **ISCO-08 8152**: Weaving and Knitting
Machine Operators.

This repository designs a forkable OSS business for a weaving and
knitting mill scheduling and logistics coordination practice: a mill
scheduling and supply-coordination robot manages crew/task records
under a governor-gated actor, so a weaving and knitting crew keeps its
own operating records instead of renting a closed workforce-management
SaaS.

**Maturity: `:implemented`.** `src/millcoord/` implements the
`MillCoordActor` as a `langgraph.graph/state-graph` (`millcoord.actor`)
wired to a `Mill Scheduling Coordination Advisor` (`millcoord.advisor`)
and an independent `MillCoordGovernor` (`millcoord.governor`),
following the itonami actor pattern (ADR-2607121000): `:intake ->
:advise -> :govern -> :decide -+-> :commit (:ok? true) +->
:request-approval (:escalate? true, human-in-the-loop interrupt) +->
:hold (:hard? true)`. HARD invariants (always hold, never
overridable): weaver provenance, mill provenance, no-actuation
(`:effect` must be `:propose`), a closed op-allowlist
(`:log-work-record`, `:schedule-crew-operation`,
`:flag-safety-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any
proposal that would directly finalize a machine-operation-execution
decision (e.g. deciding to proceed with a specific weaving or knitting
run) or a mill-safety-clearance decision (e.g. declaring a loom or
knitting machine safe to operate, or a fabric batch safe for
shipment), or that would override a mill safety officer's judgment.
Always-escalate paths (human sign-off regardless of confidence,
mapping this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always) and `:coordinate-supply-order` above
the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a mill scheduling/logistics
coordination robot performs crew scheduling, production-run/inventory/
progress-record logging and yarn/thread-stock supply-order
coordination for a weaving and knitting crew, under an actor that
proposes actions and an independent **Mill Scheduling Coordination
Governor** that gates them. The governor never dispatches hardware
itself, never operates weaving/knitting equipment on the mill floor,
and never finalizes a machine-operation-execution decision or a
mill-safety-clearance decision, and never overrides a mill safety
officer's judgment; `:high`/`:safety-critical` actions (such as a
flagged entanglement-hazard/noise-exposure/equipment-condition
concern, or an above-threshold supply order) require human sign-off.
**This actor coordinates MILL SCHEDULING/LOGISTICS ONLY — it never
operates weaving/knitting equipment itself, and it never makes a
mill-safety-clearance decision itself.**

Weaving and Knitting Machine Operators run high-speed loom and
knitting equipment — significant entanglement hazard from moving
shuttles, needles and drive mechanisms, alongside sustained noise
exposure. This is a real entanglement-hazard and noise-exposure
domain; this actor never operates that equipment and never clears it
as safe — it only schedules and logs around it, and always routes
entanglement-hazard/noise-exposure/equipment-condition safety concerns
to a human mill safety officer.

## Core Contract

```text
crew roster + mill registration + safety-reporting policy
        |
        v
Mill Scheduling Coordination Advisor -> MillCoordGovernor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a machine-operation-execution decision, finalize a
mill-safety-clearance decision, override a mill safety officer's
judgment, suppress an operating record, or disclose sensitive data
without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8152`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
