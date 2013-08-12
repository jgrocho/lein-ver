(ns leiningen.ver
  (:refer-clojure :exclude [set])
  (:require [clojure.string :refer [join]]
            [clojure.java.io :refer [file reader writer resource]]
            [leiningen.core.main :refer [abort]])
  (:import [java.io File PushbackReader]))

(def ^:private version-file-path
  (str "resources" File/separator "VERSION"))

(defn- report-missing-version-file
  "Print an error message and abort."
  []
  (binding [*out* *err*]
    (println "Could not read resources/VERSION."
             "Please create it with 'lein ver write'.")
    (abort)))

(defn- ns-to-path
  "Return a path from a ns."
  [name]
  (.replace (munge (str name)) "." File/separator))

(defn- version-template
  "Return the template file as a string, with variables replaced."
  [project]
  (let [template (slurp (resource "version.clj.tpl"))]
    (.replace template "{name}" (get-in project [:lein-ver :project-name] (ns-to-path (:name project))))))

; Semver regular expression borrowed from:
; https://github.com/mojombo/semver/issues/32#issuecomment-8380547
; For sample matches see:
; http://rubular.com/r/M7fPGmndVI
(let [semver-re #"^(\d+)\.(\d+)\.(\d+)(?:-([\dA-Za-z\-]+(?:\.[\dA-Za-z\-]+)*))?(?:\+([\dA-Za-z\-]+(?:\.[\dA-Za-z\-]+)*))?$"]
  (defn- parse-version
    "Parses the given version string, retuning a map of the version
    components."
    [s]
    (when-let [m (re-matches semver-re s)]
      {:major (read-string (nth m 1))
       :minor (read-string (nth m 2))
       :patch (read-string (nth m 3))
       :pre-release (nth m 4)
       :build (nth m 5)})))

(defn- version-string
  "Returns the given version as a string."
  [v]
  (str (join "." (filter identity (map #(% v) [:major :minor :patch])))
       (when (:pre-release v) (str "-" (:pre-release v)))
       (when (:build v) (str "+" (:build v)))))

(defn- read-version-file
  "Returns the version according to the file resources/VERSION or nil if the
  file does not exist."
  [project]
  (let [version-file (file (:root project) version-file-path)]
    (when (.exists version-file)
      (with-open [rdr (reader version-file)]
        (binding [*read-eval* false]
          (read (PushbackReader. rdr)))))))

(defn print-ver
  "Prints the project's current version."
  [project]
  (if-let [version (read-version-file project)]
    (println (version-string version))
    (report-missing-version-file)))

(defn- safe-read-string
  "Reads one object from the string s, when it's not nil."
  [s]
  (when-not (nil? s)
    (binding [*read-eval* false] (read-string s))))

(defn- read-long
  "If the string s is an integer, return that integer; nil otherwise."
  [s]
  (let [n (safe-read-string s)]
    (when (= Long (type n)) n)))

(defn- read-nil-string
  "Returns nil when s is \"nil\", s otherwise."
  [s]
  (when-not (= "nil" s) s))

(defn- write-version-file
  "Writes the given version to the file resources/VERSION."
  [project version]
  (let [version-file (file (:root project) version-file-path)
        parent (.getParentFile version-file)]
    (when-not (.exists parent) (.mkdirs parent))
    (with-open [wtr (writer version-file)]
      (.write wtr "{\n")
      (doseq
        [k [:major :minor :patch :pre-release :build]]
        (.write wtr " ")
        (.write wtr (prn-str k (k version))))
      (.write wtr "}\n"))))

(defn- replace-project-version
  "Replaces the version string in project.clj."
  [version]
  (let [proj-file (slurp "project.clj")
        matcher (.matcher (java.util.regex.Pattern/compile
                            "(?s)(\\(defproject .+?)\".+?\"")
                          proj-file)]
    (if (.find matcher)
      (spit
        "project.clj"
        (.replaceFirst matcher
                       (format "%s\"%s\"" (.group matcher 1)
                               (version-string version)))))))

(defn- update-versions
  [project version]
  (write-version-file project version)
  (replace-project-version version))

(defn- bump-major
  "Bumps the major component by 1, resetting minor and patch to 0, and
  pre-release and build to nil."
  [project]
  (let [version (read-version-file project)
        version (assoc version :pre-release nil :build nil)
        version (assoc version :minor 0 :patch 0)
        version (update-in version [:major] inc)]
    (update-versions project version)))

(defn- bump-minor
  "Bumps the minor component by 1, resetting patch to 0, and pre-release and
  build to nil."
  [project]
  (let [version (read-version-file project)
        version (assoc version :pre-release nil :build nil)
        version (assoc version :patch 0)
        version (update-in version [:minor] inc)]
    (update-versions project version)))

(defn- bump-patch
  "Bumps the patch component by 1, resetting pre-release and build to nil."
  [project]
  (let [version (read-version-file project)
        version (assoc version :pre-release nil :build nil)
        version (update-in version [:patch] inc)]
    (update-versions project version)))

(defn init
  "Initialize the project's version files."
  [project]
  (let [project-name (get-in project [:lein-ver :project-name] (ns-to-path (:name project)))
        version-file (file (get-in project [:lein-ver :src-path] (first (:source-paths project)))
                           project-name
                           "version.clj")
        parent (.getParentFile version-file)]
    (when-not (.exists parent) (.mkdirs parent))
    (when-not (.exists version-file)
      (with-open [wtr (writer version-file)]
        (.write wtr (version-template project)))))
  (let [project-version (parse-version (:version project))]
    (write-version-file project project-version)))

(defn write
  "Writes the given version to resources/VERSION and project.clj."
  [project & args]
  (let [options (apply hash-map args)
        options (into {} (for [[k v] options] [(read-string k) v]))
        major (read-long (:major options))
        minor (read-long (:minor options))
        patch (read-long (:patch options))
        pre-release (read-nil-string (:pre-release options))
        build (read-nil-string (:build options))
        version {:major major
                 :minor minor
                 :patch patch
                 :pre-release pre-release
                 :build build}]
    (update-versions project version)))

(defn set
  "Sets only the given version components."
  [project & args]
  (let [options (apply hash-map args)
        options (into {} (for [[k v] options] [(read-string k) v]))
        major (read-long (:major options))
        minor (read-long (:minor options))
        patch (read-long (:patch options))
        pre-release (read-nil-string (:pre-release options))
        build (read-nil-string (:build options))
        version (read-version-file project)
        version (merge-with #(some identity [%2 %1]) version
                            {:major major
                             :minor minor
                             :patch patch
                             :pre-release pre-release
                             :build build})]
    (update-versions project version)))

(defn bump
  "Bumps the named version component by 1, resetting the lower components."
  [project component]
  (let [version (read-version-file project)]
    (if-not version
      (report-missing-version-file)
      (condp = component
        ":major" (bump-major project)
        ":minor" (bump-minor project)
        ":patch" (bump-patch project)
        (binding [*out* *err*]
          (println "Not a valid component to bump.")
          (abort))))))

(defn check
  "Check that resources/VERSION and project.clj versions match."
  [project & args]
  (let [project-version (parse-version (:version project))
        file-version (read-version-file project)]
    (if-not file-version
      (report-missing-version-file)
      (when (not= file-version project-version)
        (binding [*out* *err*]
          (println "Versions differ between project.clj"
                   "and resources/VERSION.")
          (println "project.clj:" (version-string project-version))
          (println "    VERSION:" (version-string file-version)))
        (abort)))))

(defn ver
  "Manage a project's version."
  {:help-arglists '([write bump set check])
   :subtasks [#'write #'set #'bump #'check]}
  [project & [subtask & args]]
  (if subtask
    (case subtask
      "init"  (apply init project args)
      "write" (apply write project args)
      "set"   (apply set project args)
      "bump"  (apply bump project args)
      "check" (apply check project args))
    (print-ver project)))
