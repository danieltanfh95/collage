(ns fivetonine.collage.util
  (:require [clojure.java.io :refer [as-file file]])
  (:import java.io.File
           java.net.URI
           java.net.URL
           java.awt.image.BufferedImage
           javax.imageio.ImageIO
           javax.imageio.IIOImage
           javax.imageio.ImageWriter
           javax.imageio.ImageWriteParam
           fivetonine.collage.Frame))

(declare parse-extension)

(defn show
  "Display an image in a `JFrame`.

  Convenience function for viewing an image quickly."
  [^BufferedImage image]
  (Frame/createImageFrame "Quickview" image))

(defn copy
  "Make a deep copy of an image."
  [image]
  (let [width (.getWidth image)
        height (.getHeight image)
        type (.getType image)
        new-image (BufferedImage. width height type)]
    ;; Get data from image and set data in new-image, resulting in a copy
    ;; This also works for BufferedImages that are obtained by calling
    ;; .getSubimage on another BufferedImage.
    (.setData new-image (.getData image))
    new-image))

(defn save
  "Store an image on disk.

  Accepts optional keyword arguments.

  `:quality` - decimal, between 0.0 and 1.0. Defaults to 0.8.

  `:progressive` - boolean, `true` turns progressive saving on, `false`
  turns it off. Defaults to the default value in the ImageIO API -
  `ImageWriteParam/MODE_COPY_FROM_METADATA`. See
  [Java docs](http://docs.oracle.com/javase/7/docs/api/javax/imageio/ImageWriteParam.html).

  Examples:

    (save image \"/path/to/new/image.jpg\" :quality 1.0)
    (save image \"/path/to/new/image/jpg\" :progressive false)

  Returns the path to the saved image when saved successfully."
  [^BufferedImage image path & rest]
  (let [opts (apply hash-map rest)
        outfile (file path)
        ext (parse-extension path)
        ^ImageWriter writer (.next (ImageIO/getImageWritersByFormatName ext))
        ^ImageWriteParam write-param (.getDefaultWriteParam writer)
        iioimage (IIOImage. image nil nil)
        outstream (ImageIO/createImageOutputStream outfile)]
    ; Only compress images that can be compressed. PNGs, for example, cannot be
    ; compressed.
    (when (.canWriteCompressed write-param)
      (doto write-param
        (.setCompressionMode ImageWriteParam/MODE_EXPLICIT)
        (.setCompressionQuality (get opts :quality 0.8))))
    (when (.canWriteProgressive write-param)
      (let [mode-map {true  ImageWriteParam/MODE_DEFAULT
                      false ImageWriteParam/MODE_DISABLED}
            mode-flag (get opts :progressive)]
        (doto write-param
          (.setProgressiveMode (get mode-map
                                    mode-flag
                                    ImageWriteParam/MODE_COPY_FROM_METADATA)))))
    (doto writer
      (.setOutput outstream)
      (.write nil iioimage write-param)
      (.dispose))
    (.close outstream)
    path))

(defprotocol ImageResource
  "Coerce different image resource representations to BufferedImage."
  (as-image [x] "Coerce argument to an image."))

(extend-protocol ImageResource
  String
  (as-image [s] (ImageIO/read (as-file s)))

  File
  (as-image [f] (ImageIO/read f))

  URL
  (as-image [r] (ImageIO/read r))

  BufferedImage
  (as-image [b] b))

(defn ^BufferedImage load-image
  "Loads an image from resource."
  [resource]
  (as-image resource))

;; ## Helpers & experimental

(defn parse-extension
  "Parses the image extension from the path."
  [path]
  (last (clojure.string/split path #"\.")))

(defn sanitize-path
  "Sanitizes a path.
  Returns the sanitized path, or throws if sanitization is not possible."
  [path]
  (when-let [scheme (-> path URI. .getScheme)]
    (if-not (= "file" scheme)
      (throw (Exception. "Path must point to a local file."))
      (URI. path)))
  (URI. (str "file://" path)))
