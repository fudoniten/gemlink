(ns gemlink.gemtext-test
  (:require [gemlink.gemtext :refer :all]
            [clojure.test :refer [deftest is testing]]))

(deftest test-render-node
  (testing "render gemini node"
    (is (= (render-node [:gemini [:text "Hello"] [:h1 "Title"]])
           "Hello\n# Title")))

  (testing "render text node"
    (is (= (render-node [:text "Hello, " [:text "world!"]])
           "Hello, world!")))

  (testing "render h1 node"
    (is (= (render-node [:h1 "Header 1"])
           "# Header 1")))

  (testing "render h2 node"
    (is (= (render-node [:h2 "Header 2"])
           "## Header 2")))

  (testing "render h3 node"
    (is (= (render-node [:h3 "Header 3"])
           "### Header 3")))

  (testing "render link node"
    (is (= (render-node [:link "http://example.com" "Example"])
           "=> http://example.com Example")))

  (testing "render list node"
    (is (= (render-node [:list "Item 1" "Item 2"])
           "* Item 1\n* Item 2")))

  (testing "render quote node"
    (is (= (render-node [:quote "This is a quote"])
           "> This is a quote")))

  (testing "render preformatted node"
    (is (= (render-node [:pre "Line 1" "Line 2"])
           "```\nLine 1\nLine 2\n```")))

  (testing "render footnote with uri and text"
    (is (= (render-with-footnotes [:gemini [:text "See this" [:footnote :uri "http://example.com" :text "example"]]])
           "See this[1]\n\n=> http://example.com [1] example")))

  (testing "render footnote with text only"
    (is (= (render-with-footnotes [:gemini [:text "Note" [:footnote :text "just a note"]]])
           "Note[1]\n\n[1] just a note")))

  (testing "render multiple footnotes"
    (is (= (render-with-footnotes [:gemini [:text "First" [:footnote :uri "http://first.com" :text "first"]]
                                   [:text "Second" [:footnote :text "second"]]])
           "First[1]\nSecond[2]\n\n=> http://first.com [1] first\n[2] second")))

  (testing "footnotes flushed before next header"
    (is (= (render-with-footnotes [:gemini
                                   [:h1 "Header 1"]
                                   [:text "First" [:footnote :uri "http://first.com" :text "first"]]
                                   [:h2 "Header 2"]
                                   [:text "Second" [:footnote :text "second"]]])
           "# Header 1\nFirst[1]\n\n=> http://first.com [1] first\n## Header 2\nSecond[2]\n\n[2] second")))

  (testing "multiple footnotes in one section"
    (is (= (render-with-footnotes [:gemini
                                   [:h1 "Header 1"]
                                   [:text "First" [:footnote :uri "http://first.com" :text "first"]]
                                   [:text "Second" [:footnote :text "second"]]
                                   [:h2 "Header 2"]
                                   [:text "Third" [:footnote :uri "http://third.com" :text "third"]]])
           "# Header 1\nFirst[1]\nSecond[2]\n\n=> http://first.com [1] first\n[2] second\n## Header 2\nThird[3]\n\n=> http://third.com [3] third"))))
