;; CLJ-side unit tests for the .cljc domain interpreters — the *same* source the web (CLJS)
;; and native (kotoba-clj→WASM) run. Verifies cross-platform correctness on the JVM via
;; babashka:  bb --classpath src test/domains_bb_test.clj
(require '[kami.fsm :as fsm]
         '[kami.physics :as phys]
         '[kami.netsync :as net]
         '[kami.level :as level]
         '[kami.webgpu.ir :as ir]
         '[clojure.test :refer [deftest is run-tests]])

(deftest fsm-advance
  (is (= :move (fsm/advance fsm/default-player-fsm :idle #{:moving})))
  (is (= :idle (fsm/advance fsm/default-player-fsm :move #{:still})))
  (is (= :jump (fsm/advance fsm/default-player-fsm :idle #{:jumping})))
  (is (= :idle (fsm/advance fsm/default-player-fsm :idle #{}))  "no event → stay"))

(deftest physics-collides
  (is (phys/collides? phys/default-layers :player :bot))
  (is (phys/collides? phys/default-layers :bot :bot))
  (is (not (phys/collides? phys/default-layers :player :pickup)))
  (let [d (phys/separate phys/default-layers
                         [{:id 1 :layer :bot :x 0 :y 0} {:id 2 :layer :bot :x 10 :y 0}])]
    (is (pos? (count d)) "overlapping bots produce separation deltas")))

(deftest netsync-snapshot+interp
  (let [snap (net/snapshot net/default-schema
                           {:x 1 :y 2 :z 3 :rx 0 :ry 0 :hp 9 :tag "bot" :secret 42})]
    (is (= #{:x :y :z :rx :ry :hp} (set (keys snap))) "only synced fields")
    (is (not (contains? snap :secret)) "unsynced dropped"))
  (let [r (net/interp net/default-schema {:x 0 :hp 100} {:x 10 :hp 50} 0.5)]
    (is (= 5.0 (double (:x r))) ":lerp tweens x")
    (is (= 50 (:hp r)) ":snap jumps hp")))

(deftest level-zone
  (is (= 3000.0 (double (level/zone-radius level/default-level 0))) "full radius at t=0")
  (is (< (level/zone-radius level/default-level 5000) 3000) "storm shrinks")
  (is (level/in-zone? level/default-level [0 0] 0) "centre inside")
  (is (not (level/in-zone? level/default-level [2900 0] 5000)) "edge outside shrunk zone")
  (is (= [0 0] (level/spawn-points level/default-level :player))))

(deftest camera-rig
  (let [{:keys [eye target]} (ir/rig->camera {:distance 70 :height 48 :azimuth 0 :look-height 1} [0 0])]
    (is (< (Math/abs (- (double (eye 0)) 70.0)) 0.01) "eye.x = distance*cos(azimuth)")
    (is (= 48 (eye 1)) "eye.y = height")
    (is (= 1 (target 1)) "target.y = look-height")))

(deftest ir-helpers
  (is (ir/valid? (ir/render-ir (ir/sky [0.7 0.8 0.9] [-0.4 -0.85 -0.35] [1 1 1])
                               [(ir/instance [0 0 0] [1 0 0] [2 5])])))
  (is (not (ir/valid? {:globals {} :instances [{:bad true}]}))))

(deftest edge-cases
  ;; fsm: jump state has both exits
  (is (= :move (fsm/advance fsm/default-player-fsm :jump #{:moving})))
  (is (= :idle (fsm/advance fsm/default-player-fsm :jump #{:still})))
  ;; physics: non-colliding layers produce no separation
  (is (empty? (phys/separate phys/default-layers
                             [{:id 1 :layer :player :x 0 :y 0} {:id 2 :layer :pickup :x 5 :y 0}]))
      "player+pickup don't collide → no deltas")
  (is (= 34.0 (phys/radius phys/default-layers :player)))
  ;; netsync: apply-snapshot merges only synced fields
  (let [merged (net/apply-snapshot net/default-schema {:x 0 :hp 1 :keep 7} {:x 9 :hp 2 :tag "x"})]
    (is (= 9 (:x merged)) "synced field overwritten")
    (is (= 7 (:keep merged)) "non-schema local field preserved")
    (is (not (contains? merged :tag)) "non-synced incoming dropped"))
  ;; level: radius floors at :min-radius; bots spawn list
  (is (= 200.0 (double (level/zone-radius level/default-level 100000))) "floors at min-radius")
  (is (= 6 (count (level/spawn-points level/default-level :bots))))
  ;; ir: frame-ir carries globals + instances
  (let [f (ir/render-ir (ir/sky [0.1 0.2 0.3] [0 -1 0] [1 1 1]) [(ir/instance [0 0 0] [1 0 0] [1 1])])]
    (is (= [0.1 0.2 0.3] (get-in f [:globals :sky :horizon])))
    (is (= 1 (count (:instances f))))))

(let [{:keys [fail error]} (run-tests)]
  (System/exit (if (pos? (+ fail error)) 1 0)))
