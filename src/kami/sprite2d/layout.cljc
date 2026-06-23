(ns kami.sprite2d.layout
  "Pure 2D *layout* — the draw list (what is drawn, where, in what order) as DATA, so the
   rendering decisions are testable in CLJ without a canvas: camera follow, sprite/variant
   pick (the raging-gorilla swap), depth order, and screen orientation (the classic W/S-up bug).
   `.cljs` painter (isekai.sprite2d) turns this list into canvas ops; `bb` golden-tests it.")

(defn- sq [x] (* x x))

(defn scale-k
  "World→screen scale for a viewport of width W (density-independent, matches the painter)."
  [scene W]
  (* (get-in scene [:render/sprite2d :scale] 0.34) (/ W 900.0)))

(defn camera
  "The camera anchor (player world position) for this snapshot, or [0 0]."
  [snap]
  (if-let [p (first (filter #(= (:tag %) "player") snap))]
    [(nth (:pos p) 0) (nth (:pos p) 1)]
    [0 0]))

(defn draw-list
  "Ordered sprite draw ops for a frame, in screen space (painter order: north/far first so
   nearer things layer over them). Each op: {:tag :variant :sprite :sx :sy}. Pure — no canvas.
   Screen y grows downward, but world +y maps UP (so 'up' on the keyboard is up on screen)."
  [scene snap W H]
  (let [cfg (:render/sprite2d scene)
        k (scale-k scene W)
        sprites (:sprites scene)
        [px py] (camera snap)
        sx (fn [x] (+ (/ W 2.0) (* (- x px) k)))
        sy (fn [y] (- (/ H 2.0) (* (- y py) k)))   ;; world +y = screen up
        aw (:awake cfg)
        w2 (when aw (sq (:within aw 1000)))]
    (->> snap
         ;; painter order: higher world-y (north, drawn smaller/up) goes behind → sort y desc
         (sort-by #(- (nth (:pos %) 1)))
         (keep (fn [e]
                 (let [tag (:tag e) ex (nth (:pos e) 0) ey (nth (:pos e) 1)
                       near? (boolean (and aw (= tag (:tag aw))
                                           (< (+ (sq (- ex px)) (sq (- ey py))) w2)))
                       spk (if near? (:variant aw) (keyword tag))
                       sp (or (get sprites spk) (get sprites (keyword tag)))]
                   (when sp {:tag tag
                             :variant (when near? (:variant aw))
                             :sprite sp
                             :sx (sx ex) :sy (sy ey)}))))
         vec)))
