(ns ^:figwheel-always rum-figwheel-lein.core
  (:require [rum :as r]))

(enable-console-print!)

(def grid-size 12)

(def tick-in-ms 250)

;; helpers

(defn rand-in-size 
  ([padding] 
   (+ padding (rand-int (- grid-size (* 2 padding)))))
  ([] 
   (rand-in-size 0)))

(defn rand-pos 
  ([padding] 
   (seq [(rand-in-size padding) (rand-in-size padding)]))
  ([]
   (rand-pos 0)))

(defn collide? 
  [pos snake]
  (some #{pos} snake))

(defn rand-apple
  [snake]
  (first 
    (remove 
      #(collide? % snake)
      (repeatedly rand-pos))))

(defn hit-border? 
  [pos]
  (or (some #(< (dec grid-size) %) pos)
      (some neg? pos)))

(def dir-offset {:up [0 -1]
                 :down [0 1]
                 :left [-1 0]
                 :right [1 0]})

(defn next-snake-head [snake dir] 
  (map + (first snake) (dir dir-offset)))

;; the world

(defn world-setup
  []
  (let [snake (seq [(rand-pos 2)])
        apple (rand-apple snake)
        dir (rand-nth (keys dir-offset))] 
    {:running false
     :alive true
     :snake snake
     :dir dir
     :apple apple}))

(defonce world (atom (world-setup)))

;; manipulation of the world

(defn toggle-pause!
  []
  (swap! world 
         #(assoc % :running 
                 (and 
                   (:alive %) 
                   (not (:running %))))))

(defn restart!
  []
  (swap! world world-setup))

(defn change-dir! 
  [new-dir]
  (swap! world assoc :dir new-dir))

(def key-mapping {38 :up
                  40 :down
                  37 :left
                  39 :right
                  80 :pause
                  82 :restart })

(defn handle-command! 
  [cmd]
  (case cmd 
    :pause (toggle-pause!)
    :restart (restart!)
    (change-dir! cmd)))

(defn next-world
  [world]
  (let [{:keys [snake dir apple]} world
        next-head (next-snake-head snake dir)
        eat? (= next-head (:apple world))
        body (if eat? snake (butlast snake))
        alive (and (not (hit-border? next-head))
                   (not (collide? next-head snake)))  
        next-snake (conj body next-head)
        next-apple (if eat?
                     (rand-apple next-snake)
                     apple)]
    (assoc world 
           :running (and alive (:running world))
           :alive alive
           :snake next-snake
           :apple next-apple
           )))

(defn tick! 
  []
  (when (:running @world)
    (swap! world next-world)))

;; render the world

(r/defc debug < r/reactive []
  [:div {:class "debug"} "Debug of world: " (str (r/react world))])

(r/defc render-world < r/reactive []
  [:div 
   [:h3 (case (map (r/react world) [:running :alive])
          [true true] "Eat up - press [p] to pause"
          [false true] "Paused - press [p] to continue"
          (str "You are dead! You ate " (dec (count (:snake (r/react world)))) " apples!  Restart with [r]"))]
   [:div {:class "playfield"} 
    [:table
     (for [y (range grid-size)]
       [:tr
        (for [x (range grid-size)]
          (let [p [x y]]
            [:td 
             {:class (cond 
                       (= p (:apple (r/react world))) "apple"
                       (collide? p (:snake (r/react world))) "snake"
                       :else ""
                       )}]))])]]
   [:div {:class "controlls"}   
    [:button {:on-click restart!} "(R)estart"]
    [:button {:on-click toggle-pause!} "Toggle (P)ause"]]
   (debug)])

;; setup

(r/mount (render-world)
         (.getElementById js/document "app"))

(defonce key-watch 
  (.addEventListener js/document "keydown" 
                     #(when-let [cmd (get key-mapping (.-keyCode %))]
                        (handle-command! cmd))))

(defonce tick-watch
  (js/setInterval tick! tick-in-ms))
