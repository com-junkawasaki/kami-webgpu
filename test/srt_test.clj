(ns srt-test
  "Golden tests for kami.srt — the SubRip subtitle hiccup. They pin the HH:MM:SS,mmm timestamp
   formatting (incl. fractional seconds, hours/minutes rollover), auto-numbered cues, multi-line cue
   text, and the blank-line cue separator."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.srt :as srt]))

(deftest timestamps
  (is (= "00:00:00,000" (srt/ts 0)))
  (is (= "00:00:01,000" (srt/ts 1)))
  (is (= "00:00:08,250" (srt/ts 8.25))  "fractional seconds → millis")
  (is (= "00:01:30,123" (srt/ts 90.123)) "minute rollover")
  (is (= "01:01:01,500" (srt/ts 3661.5)) "hour + minute rollover"))

(deftest cues
  (is (= "00:00:01,000 --> 00:00:04,000\nHello"
         (srt/cue [:cue {:from 1 :to 4} "Hello"])) "single cue body, no index")
  (is (= "00:00:05,500 --> 00:00:08,000\nline 1\nline 2"
         (srt/cue [:cue {:from 5.5 :to 8} "line 1" "line 2"])) "multi-line text"))

(deftest a-document
  (let [src (srt/srt
              [:cue {:from 1 :to 4} "Hello world"]
              [:cue {:from 5.5 :to 8.25} "Second line" "multi-line text"])]
    (is (= (str "1\n00:00:01,000 --> 00:00:04,000\nHello world\n\n"
                "2\n00:00:05,500 --> 00:00:08,250\nSecond line\nmulti-line text\n")
           src) "auto-numbered, blank-line separated, trailing newline")))

(let [{:keys [fail error]} (run-tests 'srt-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "srt tests failed" {:fail fail :error error}))))
