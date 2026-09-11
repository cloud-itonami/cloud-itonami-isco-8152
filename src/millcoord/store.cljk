(ns millcoord.store
  "SSoT for the ISCO-08 8152 weaving and knitting machine operators mill
  scheduling/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a mill scheduling/logistics coordination robot performs
  crew scheduling, production-run/inventory/progress-record logging
  and yarn/thread-stock supply-order coordination for a weaving and
  knitting crew under this advisor/governor pair, which never
  dispatches hardware itself, never operates weaving/knitting
  equipment itself, and never finalizes a machine-operation-execution
  decision or a mill-safety-clearance decision, and never overrides a
  mill safety officer's judgment — those remain the mill safety
  officer's exclusive judgment). Modeled closely on
  cloud-itonami-isco-8122's platingcoord.store.

  Domain:

    weaver — a registered weaving/knitting machine operator crew
             member (:weaver-id, :name). ('weaver' here names the
             closed-allowlist crew-record entity distinctly from
             'operator' the deploying/certified business entity used
             elsewhere in this repo's docs — see the Robotics premise
             in README.md.)
    mill   — a registered weaving/knitting mill/line {:mill-id :name
             :max-supply-cost number}. `:max-supply-cost` is an
             informational registered ceiling used only to decide
             whether a `:coordinate-supply-order` proposal escalates
             to human sign-off (the governor never blocks a
             within-threshold order outright; it only decides commit
             vs. escalate).
    record — a committed operating record (a logged production-run/
             inventory/progress entry, a scheduled crew/shift
             operation, a flagged safety concern, or a coordinated
             yarn/thread-stock supply order) — written ONLY via
             commit-record!. This actor coordinates mill
             scheduling/logistics ONLY — a `record` is a coordination
             artifact, never a machine-operation-execution act, never
             a mill-safety-clearance decision, and never a mill safety
             officer's-judgment override.
    ledger — append-only audit trail, commit or hold.")

(defprotocol Store
  (weaver [s weaver-id])
  (mill [s mill-id])
  (records-of [s weaver-id])
  (ledger [s])
  (register-weaver! [s weaver])
  (register-mill! [s mill])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (weaver [_ weaver-id] (get-in @a [:weavers weaver-id]))
  (mill [_ mill-id] (get-in @a [:mills mill-id]))
  (records-of [_ weaver-id] (filter #(= weaver-id (:weaver-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-weaver! [s w]
    (swap! a assoc-in [:weavers (:weaver-id w)] w) s)
  (register-mill! [s m]
    (swap! a assoc-in [:mills (:mill-id m)] m) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:weavers {} :mills {} :records [] :ledger []}
                                    seed)))))
