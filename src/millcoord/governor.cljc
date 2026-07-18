(ns millcoord.governor
  "MillCoordGovernor — the independent safety/scope layer gating every
  mill scheduling/logistics proposal an advisor may make for a
  weaving and knitting crew. The governor never dispatches hardware
  itself, never operates weaving/knitting equipment itself, and never
  finalizes a machine-operation-execution decision (e.g. deciding to
  proceed with a specific weaving or knitting run) or a
  mill-safety-clearance decision (e.g. declaring a loom or knitting
  machine safe to operate, or a fabric batch safe for shipment), and
  never overrides a mill safety officer's judgment — those are
  permanently out of this actor's scope and remain a mill safety
  officer's exclusive judgment (README's 'Robotics premise': this
  actor coordinates MILL SCHEDULING/LOGISTICS ONLY — it never operates
  weaving/knitting equipment itself). Modeled closely on
  cloud-itonami-isco-8122's platingcoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. weaver provenance      — the crew member must be independently
                                verified/registered before any action.
    2. mill provenance        — the weaving/knitting mill/line must be
                                independently verified/registered
                                before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never operates weaving/knitting
                                equipment itself; it only gates what
                                the advisor may coordinate).
    4. closed op-allowlist    — only :log-work-record,
                                :schedule-crew-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                machine-operation-execution decision
                                (e.g. deciding to proceed with a
                                specific weaving or knitting run), or a
                                mill-safety-clearance decision (e.g.
                                declaring a loom or knitting machine
                                safe to operate, or a fabric batch safe
                                for shipment), or to override a mill
                                safety officer's judgment, is a hard,
                                permanent block (checked both against
                                the proposed :op and, defense-in-depth,
                                against the proposal's :rationale text
                                — matched as full finalization/
                                execution ACTION phrases such as
                                \"finalize the weaving operation\" /
                                \"declare the mill safety cleared\" /
                                \"override the mill safety officer's
                                judgment\", never as bare nouns like
                                \"loom\", \"shuttle\" or \"needle\", so
                                the check can never self-trip on the
                                advisor's own routine rationale text,
                                e.g. \"logged work record for weaver
                                …\" or \"scheduled crew operation for
                                weaving/knitting task …\" or \"…routed
                                for mill safety officer review\" — all
                                three legitimately contain bare domain
                                nouns but none is a finalization
                                action, and all are exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (an entanglement-hazard / noise-
                                exposure / equipment-condition concern
                                always escalates to a human, never
                                auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`).

  This actor coordinates mill scheduling/logistics ONLY — it never
  operates weaving/knitting equipment itself, and it never makes a
  mill-safety-clearance decision itself; those decisions always route
  to a human mill safety officer, either via a hard permanent block on
  the op-allowlist (rules 4/5 above) or via a mandatory escalation
  (rule 6 above)."
  (:require [clojure.string :as str]
            [millcoord.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-weaving-decision :finalize-weaving-operation
    :finalize-knitting-operation
    :authorize-loom-run :authorize-knitting-run
    :proceed-with-weaving-run :proceed-with-knitting-run
    :finalize-mill-safety-clearance
    :declare-machine-safe-for-operation
    :declare-fabric-batch-safe-for-shipment
    :clear-batch-for-shipment
    :override-mill-safety-officer-judgment
    :override-safety-officer-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("loom", "shuttle", "needle", "yarn", "thread", "fabric", "mill",
;; "weaving", "knitting", "safety", "officer") — so this can never
;; match inside the mock advisor's own default rationale text (which
;; legitimately contains those bare nouns, e.g. "weaving/knitting
;; task" / "mill safety officer review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the weaving run" "proceed with the knitting run"
   "authorize the loom run" "authorize the knitting run"
   "finalize the weaving decision" "finalize the weaving operation"
   "finalize the knitting operation"
   "declare the machine safe for operation" "declare the loom safe for operation"
   "declare the knitting machine safe for operation"
   "declare the fabric batch safe for shipment" "declare the batch safe for shipment"
   "clear the batch for shipment" "clear the fabric batch for shipment"
   "finalize the mill safety clearance"
   "declare the mill safety cleared"
   "override the mill safety officer's judgment"
   "override the safety officer's judgment"
   "override mill safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower-case (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal weaver-record mill-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? weaver-record)
      (conj {:rule :no-weaver
             :detail "未登録 weaver への提案は不可（weaver record は独立して検証・登録済みでなければならない）"})

      (nil? mill-record)
      (conj {:rule :no-mill
             :detail "未登録 mill への提案は不可（mill record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は織布・編立作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "機械運転実行判断・ミル安全(mill-safety)クリアランス判断の確定、および mill safety officer の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `millcoord.store/Store`. Pure — never mutates
  the store, never dispatches a weaving/knitting operation."
  [request _context proposal store]
  (let [weaver-record (store/weaver store (:weaver-id request))
        mill-record (some->> (:mill-id proposal) (store/mill store))
        hard (hard-violations proposal weaver-record mill-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
