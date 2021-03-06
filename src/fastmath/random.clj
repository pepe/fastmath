(ns fastmath.random
  "Various random and noise functions.

  Namespace defines various random number generators (RNGs), different types of random functions, sequence generators and noise functions.

  #### RNGs

  You can use a selection of various RNGs defined in [Apache Commons Math](http://commons.apache.org/proper/commons-math/apidocs/org/apache/commons/math3/random/package-summary.html) library.

  Currently supported RNGs:

  * `:jdk` - default java.util.Random
  * `:mersenne` - MersenneTwister
  * `:isaac` - ISAAC
  * `:well512a`, `:well1024a`, `:well19937a`, `:well19937c`, `:well44497a`, `:well44497b` - several WELL variants

  To create your RNG use [[rng]] multimethod. Pass RNG name and (optional) seed. Returned RNG is equipped with [[RNGProto]] protocol with methods: [[irandom]], [[lrandom]], [[frandom]] [[drandom]], [[grandom]], [[brandom]] which return random primitive value with given RNG.

  ```
  (let [rng (rng :isaac 1337)]
    (irandom rng))
  ```

  For conveniency default RNG (`:mesenne`) with following functions are created: [[irand]], [[lrand]], [[frand]], [[drand]], [[grand]], [[brand]].

  Each prefix denotes returned type:

  * i - int
  * l - long
  * f - float
  * d - double
  * g - gaussian (double)
  * b - boolean

  Check individual function for parameters description.

  #### Random Vector Sequences

  Couple of functions to generate sequences of numbers or vectors.
  You can generate sequence of `double`, [[Vec2]], [[Vec3]] or [[Vec4]] types. Just pass the size to creator function.

  To create generator call [[sequence-generator]] with generator name and vector size [1,4].
  Following generators are available:

  * `:halton` - Halton low-discrepancy sequence; range [0,1]
  * `:sobol` - Sobol low-discrepancy sequence; range [0,1]
  * `:sphere` - uniformly random distributed on unit sphere
  * `:gaussian` - gaussian distributed (mean=0, stddev=1)
  * `:default` - uniformly random; range:[0,1]

  After creation you get function equivalent to `repeatedly`.

  #### Noise

  List of continuous noise functions (1d, 2d and 3d):

  * `:value` - value noise
  * `:gradient` - gradient noise (improved Ken Perlin version)
  * `:simplex` - simplex noise

  First two (`:value` and `:gradient`) can use 4 different interpolation types: `:none`, `:linear`, `:hermite` (cubic) and `:quintic`.
  
  All can be used as into:

  * Noise - pure noise value, create with [[single-noise]]
  * FBM - fractal brownian motion, create with [[fbm-noise]]
  * Billow - billow noise, [[billow-noise]]
  * RidgedMulti - ridged multi, [[ridgedmulti-noise]]

  Noise creation requires detailed configuration which is simple map of following keys:

  * `:seed` - seed as integer
  * `:noise-type` - type of noise: `:value`, `:gradient` (default), `:simplex`
  * `:interpolation` - type of interpolation (for value and gradient): `:none`, `:linear`, `:hermite` (default) or `:quintic`
  * `:octaves` - number of octaves for combined noise (like FBM), default: 6
  * `:lacunarity` - scaling factor for combined noise, default: 2.00
  * `:gain` - amplitude scaling factor for combined noise, default: 0.5
  * `:normalize?` - should be normalized to `[0,1]` range (true, default) or to `[-1,1]` range (false)

  For usage convenience 3 ready to use functions are prepared. Return is normalized to `[0,1]` range:

  * [[noise]] - Perlin Noise (gradient noise, 6 octaves, quintic interpolation)
  * [[vnoise]] - Value Noise (as in Processing, 6 octaves, hermite interpolation)
  * [[simplex]] - Simpled Noise (6 octaves)

  ##### Discrete Noise

  [[discrete-noise]] is a 1d or 2d hash function for given integers. Returns double from `[0,1]` range.

  #### Distribution

  Various real and integer distributions. See [[DistributionProto]] and [[RNGProto]] for functions.

  To create distribution call [[distribution]] multimethod with name as a keyword and map as parameters."
  {:metadoc/categories {:rand "Random number generation"
                        :noise "Noise functions"
                        :gen "Random sequence generation"
                        :dist "Distributions"}}
  (:require [fastmath.core :as m]
            [fastmath.vector :as v]
            [metadoc.examples :refer :all])
  (:import [org.apache.commons.math3.random RandomGenerator ISAACRandom JDKRandomGenerator MersenneTwister
            Well512a Well1024a Well19937a Well19937c Well44497a Well44497b
            RandomVectorGenerator HaltonSequenceGenerator SobolSequenceGenerator UnitSphereRandomVectorGenerator
            EmpiricalDistribution]
           [fastmath.java.noise Billow RidgedMulti FBM NoiseConfig Noise]
           [org.apache.commons.math3.distribution AbstractRealDistribution RealDistribution BetaDistribution CauchyDistribution ChiSquaredDistribution EnumeratedRealDistribution ExponentialDistribution FDistribution GammaDistribution, GumbelDistribution, LaplaceDistribution, LevyDistribution, LogisticDistribution, LogNormalDistribution, NakagamiDistribution, NormalDistribution, ParetoDistribution, TDistribution, TriangularDistribution, UniformRealDistribution WeibullDistribution]
           [org.apache.commons.math3.distribution IntegerDistribution AbstractIntegerDistribution BinomialDistribution EnumeratedIntegerDistribution, GeometricDistribution, HypergeometricDistribution, PascalDistribution, PoissonDistribution, UniformIntegerDistribution, ZipfDistribution]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
(m/use-primitive-operators)

;; Type hinted functions generating random value
(defn- next-random-value-long
  "Generate next long.

  * arity 0 - from 0 to maximum long value
  * arity 1 - from 0 to provided integer (excluded)
  * arity 2 - from the provided range (included, excluded)"
  (^long [^RandomGenerator r] (.nextLong r))
  (^long [^RandomGenerator r ^long mx] (mod (.nextLong r) mx))
  (^long [r ^long mn ^long mx]
   (let [diff (- mx mn)]
     (if (zero? diff) mn
         (+ mn (next-random-value-long r diff))))))

(defn- next-random-value-double
  "Generate next double.

  * arity 0 - from 0 to 1 (exluded)
  * arity 1 - from 0 to provided double (excluded)
  * arity 2 - from the provided range (included, excluded)"
  (^double [^RandomGenerator r] (.nextDouble r))
  (^double [^RandomGenerator r ^double mx] (* (.nextDouble r) mx))
  (^double [r ^double mn ^double mx]
   (let [diff (- mx mn)]
     (if (zero? diff) mn
         (+ mn (next-random-value-double r diff))))))

(defn- next-random-value-gaussian
  "Generate next random value from normal distribution.

  * arity 0 - N(0,1)
  * arity 1 - N(0,par)
  * arity 2 - N(par1,par2)"
  (^double [^RandomGenerator r] (.nextGaussian r))
  (^double [^RandomGenerator r ^double mx] (* (.nextGaussian r) mx))
  (^double [r ^double mn ^double mx]
   (let [diff (- mx mn)]
     (if (zero? diff) mn
         (+ mn (next-random-value-gaussian r diff))))))

(defprotocol RNGProto
  "Defines set of random functions for different RNGs or distributions returning primitive values."
  (^{:metadoc/categories #{:rand}}
   irandom [t] [t mx] [t mn mx]
   "Random integer.

For RNGs:
As default returns random integer from full integer range. 
When `mx` is passed, range is set to `[0, mx)`. When `mn` is passed, range is set to `[mn, mx)`.

See [[irand]].

For distributions, just returns random integer (call without parameters).")
  (^{:metadoc/categories #{:rand}}
   drandom [t] [t mx] [t mn mx]
   "Random double.

For RNGs:
As default returns random double from `[0,1)` range.
When `mx` is passed, range is set to `[0, mx)`. When `mn` is passed, range is set to `[mn, mx)`.

See [[drand]].

For distributions, just returns random double (call without parameters).")
  (^{:metadoc/categories #{:rand}} lrandom [t] [t mx] [t mn mx]
   "Random long.

For RNGs:
As default returns random long from full long range. 
When `mx` is passed, range is set to `[0, mx)`. When `mn` is passed, range is set to `[mn, mx)`.

See [[lrand]].

For distributions, just returns random long (call without parameters).")
  (^{:metadoc/categories #{:rand}} frandom [t] [t mx] [t mn mx]
   "Random float.

For RNGs:
As default returns random float from `[0,1)` range.
When `mx` is passed, range is set to `[0, mx)`. When `mn` is passed, range is set to `[mn, mx)`.

See [[frand]].

For distributions, just returns random float (call without parameters).")
  (^{:metadoc/categories #{:rand}} grandom [t] [t std] [t mean std]
   "Random double from gaussian distribution.
As default returns random double from `N(0,1)`. 
When `std` is passed, `N(0,std)` is used. When `mean` is passed, distribution is set to `N(mean, std)`.

See [[grand]].")
  (^{:metadoc/categories #{:rand}} brandom [t] [t thr]
   "Boolean random.
Returns true or false with equal probability. You can set probability for `true` setting `thr` (from `[0-1]` range).

See [[brand]].")
  (^{:metadoc/categories #{:rand}} set-seed! [t v] "Sets seed. Returns RNG or distribution itself.")
  (^{:metadoc/categories #{:rand}} ->seq [t] [t n] "Returns sequence of random samples limited to optional `n` values."))

;; Extend RandomGenerator interface with functions created by macro `next-random-value-fn`. This way all RNG classes are enriched with new, more convenient functions.
;;
;; Note that `grandom` is under special care due to different [mn mx] range meaning.

(extend RandomGenerator 
  RNGProto
  {:irandom (comp unchecked-int next-random-value-long)
   :lrandom next-random-value-long
   :frandom (comp float next-random-value-double)
   :drandom next-random-value-double
   :grandom (fn
              ([t] (next-random-value-gaussian t))
              ([t std] (next-random-value-gaussian t std))
              ([t ^double mean ^double std] (next-random-value-gaussian t mean (+ mean std))))
   :brandom (fn
              ([^RandomGenerator t] (.nextBoolean t))
              ([t ^double thr] (< (next-random-value-double t) thr)))
   :set-seed! #(do
                 (.setSeed ^RandomGenerator %1 (long %2))
                 %1)
   :->seq (fn
            ([^RandomGenerator t] (repeatedly #(next-random-value-double t)))
            ([^RandomGenerator t n] (repeatedly n #(next-random-value-double t))))})

;; Helper macro which creates RNG object of given class and/or seed.
(defmacro ^:private create-object-with-seed
  "Create object of the class with (or not) given seed. Used to create RNG."
  [cl seed]
  `(if-let [arg# ~seed]
     (new ~cl (int arg#))
     (new ~cl)))

(defmulti rng
  "Create RNG for given name (as keyword) and optional seed. Return object enhanced with [[RNGProto]]. See: [[rngs-list]] for names."
  {:metadoc/categories #{:rand}}
  (fn [m & _] m))

(defmethod rng :mersenne [m & [seed]]
  (create-object-with-seed MersenneTwister seed))
(defmethod rng :isaac [m & [seed]]
  (create-object-with-seed ISAACRandom seed))
(defmethod rng :well512a [m & [seed]]
  (create-object-with-seed Well512a seed))
(defmethod rng :well1024a [m & [seed]]
  (create-object-with-seed Well1024a seed))
(defmethod rng :well19937a [m & [seed]]
  (create-object-with-seed Well19937a seed))
(defmethod rng :well19937c [m & [seed]]
  (create-object-with-seed Well19937c seed))
(defmethod rng :well44497a [m & [seed]]
  (create-object-with-seed Well44497a seed))
(defmethod rng :well44497b [m & [seed]]
  (create-object-with-seed Well44497b seed))
(defmethod rng :jdk [m & [seed]]
  (create-object-with-seed JDKRandomGenerator seed))

;; List of randomizers
(def ^{:metadoc/categories #{:rand}
       :doc "List of all possible RNGs."
       :metadoc/examples [(example "Contains" (sort rngs-list))]}
  rngs-list (remove #{:default} (keys (methods rng))))


(add-examples rng
  (example-session "Creating" (rng :mersenne) (rng :isaac 1234))
  (example "Using" (irandom (rng :mersenne 999) 15 25)))

(defsnippet rngproto-snippet
  "Show [[RNGProto]] methods."
  (let [rng (rng :well44497b)]
    (f rng)))

(add-examples irandom (example-snippet "integer" rngproto-snippet irandom))
(add-examples lrandom (example-snippet "long" rngproto-snippet lrandom))
(add-examples drandom (example-snippet "double" rngproto-snippet drandom))
(add-examples frandom (example-snippet "float" rngproto-snippet frandom))
(add-examples grandom (example-snippet "gaussian double" rngproto-snippet grandom))
(add-examples brandom (example-snippet "boolean" rngproto-snippet brandom))

(add-examples set-seed!
  (example "Set seed for the RNG object" {:test-value 10} (let [rng (rng :isaac)]
                                                            (set-seed! rng 1234)
                                                            (irandom rng 10 15))))

;; ### Default RNG

(def ^{:doc "Default RNG - Mersenne Twister"
       :metadoc/categories #{:rand}
       :metadoc/examples [(example-session "Usage"
                            (set-seed! default-rng 111)
                            (irandom default-rng)
                            (set-seed! default-rng 999)
                            (irandom default-rng)
                            (set-seed! default-rng 111)
                            (irandom default-rng))]}
  default-rng (rng :mersenne))

(def ^{:doc "Random float number with Mersenne Twister RNG."
       :metadoc/categories #{:rand}
       :metadoc/examples [(example-session "Usage" (frand) (frand 10) (frand 10 20))]}
  frand (partial frandom default-rng))

(def ^{:doc "Random boolean with Mersenne Twister RNG."
       :metadoc/categories #{:rand}
       :metadoc/examples [(example-session "Usage" (brand) (brand 0.1))
                          (example "Count number of `true` values with probability 0.15" (count (filter true? (repeatedly 100000 #(brand 0.15)))))]} 
  brand (partial brandom default-rng))

(defn drand
  "Random double number with Mersenne Twister RNG."
  {:metadoc/categories #{:rand}
   :metadoc/examples [(example-session "Usage" (drand) (drand 10) (drand 10 20))]}
  (^double [] (drandom default-rng))
  (^double [mx] (drandom default-rng mx))
  (^double [mn mx] (drandom default-rng mn mx)))

(defn grand
  "Random gaussian double number with Mersenne Twister RNG."
  {:metadoc/categories #{:rand}
   :metadoc/examples [(example-session "Usage" (grand) (grand 10) (grand 10 20))]}
  (^double [] (grandom default-rng))
  (^double [stddev] (grandom default-rng stddev))
  (^double [mean stddev] (grandom default-rng mean stddev)))

(defn irand
  "Random integer number with Mersenne Twister RNG."
  {:metadoc/categories #{:rand}
   :metadoc/examples [(example-session "Usage" (irand) (irand 10) (irand 10 20))]}
  (^long [] (irandom default-rng))
  (^long [mx] (irandom default-rng mx))
  (^long [mn mx] (irandom default-rng mn mx)))

(defn lrand
  "Random long number with Mersenne Twister RNG."
  {:metadoc/categories #{:rand}
   :metadoc/examples [(example-session "Usage" (lrand) (lrand 10) (lrand 10 20))]}
  (^long [] (lrandom default-rng))
  (^long [mx] (lrandom default-rng mx))
  (^long [mn mx] (lrandom default-rng mn mx)))

(defmacro randval
  "Retrun value with given probability (default 0.5)"
  {:metadoc/categories #{:rand}}
  ([v1 v2]
   `(if (brandom default-rng) ~v1 ~v2))
  ([prob v1 v2]
   `(if (brandom default-rng ~prob) ~v1 ~v2)))

(add-examples randval
  (example-session "Usage" (randval :val-one :val-two) (randval 0.001 :low-probability :high-probability))
  (example "Check probability of nil (should return value around 1000)." (count (filter nil? (repeatedly 1000000 #(randval 0.001 nil 101))))))

(defn- commons-math-generators
  "Generators from commons math"
  [gen ^long size]
  (let [s (m/constrain size 1 4)
        ^RandomVectorGenerator g (case gen
                                   :halton (HaltonSequenceGenerator. s)
                                   :sobol (SobolSequenceGenerator. s)
                                   :sphere (UnitSphereRandomVectorGenerator. s))
        gf (case s
             1 #(aget (.nextVector g) 0)
             2 #(v/array->vec2 (.nextVector g))
             3 #(v/array->vec3 (.nextVector g))
             4 #(v/array->vec4 (.nextVector g)))]
    #(repeatedly gf)))

(defn- random-generators
  "Random generators"
  [gen ^long size]
  (let [s (m/constrain size 1 4)
        g (case gen
            :default drand
            :gaussian grand)
        gf (case s
             1 g
             2 (partial v/generate-vec2 g)
             3 (partial v/generate-vec3 g)
             4 (partial v/generate-vec4 g))]
    #(repeatedly gf)))

;; Sequence creators

(defmulti
  ^{:doc "Create Sequence generator. See [[sequence-generators-list]] for names. Parameter `size` describes number of dimensions (1-4).

Values are from following values:

* `:halton`, `:sobol`, `:default` - range `[0-1]`
* `:gaussian` - from `N(0,1)` distribution
* `:sphere` -  from surface of unit sphere (ie. euclidean distance from origin equals 1.0)" 
    :metadoc/categories #{:gen}}
  sequence-generator (fn [gen size] gen))
(defmethod sequence-generator :halton [gen size] (commons-math-generators gen size))
(defmethod sequence-generator :sobol [gen size] (commons-math-generators gen size))
(defmethod sequence-generator :sphere [gen size] (commons-math-generators gen size))
(defmethod sequence-generator :gaussian [gen size] (random-generators gen size))
(defmethod sequence-generator :default [gen size] (random-generators gen size))

(def ^{:doc "List of random sequence generator. See [[sequence-generator]]."
       :metadoc/examples [(example "Generator names." (sort sequence-generators-list))]
       :metadoc/categories #{:gen}}
  sequence-generators-list (keys (methods sequence-generator)))

(add-examples sequence-generator
  (example "Usage (2d)" (let [gen (sequence-generator :halton 2)]
                          (take 5 (gen))))
  (example "Usage (1d)" (let [gen (sequence-generator :sobol 1)]
                          (take 5 (gen))))
  (example-image "Halton plot (1000 samples)" "images/r/halton.jpg")
  (example-image "Sobol plot (1000 samples)" "images/r/sobol.jpg")
  (example-image "Sphere plot (1000 samples)" "images/r/sphere.jpg")
  (example-image "Gaussian plot (1000 samples)" "images/r/gaussian.jpg")
  (example-image "Default plot (1000 samples)" "images/r/default.jpg"))

;; ## Noise

(def ^{:doc "List of possible noise interpolations as a map of names and values."
       :metadoc/categories #{:noise}
       :metadoc/examples [(example "List of names (keys)" (keys interpolations))]}
  interpolations {:none NoiseConfig/INTERPOLATE_NONE
                  :linear NoiseConfig/INTERPOLATE_LINEAR
                  :hermite NoiseConfig/INTERPOLATE_HERMITE
                  :quintic NoiseConfig/INTERPOLATE_QUINTIC})

(def ^{:doc "List of possible noise types as a map of names and values."
       :metadoc/categories #{:noise}
       :metadoc/examples [(example "List of names (keys)" (keys noise-types))]}
  noise-types {:value NoiseConfig/NOISE_VALUE
               :gradient NoiseConfig/NOISE_GRADIENT
               :simplex NoiseConfig/NOISE_SIMPLEX})

(defn- noise-config-obj
  "Create noise configuration object based on map."
  [{:keys [seed noise-type interpolation octaves lacunarity gain normalize?]}]
  (NoiseConfig. seed
                (or (noise-types noise-type) NoiseConfig/NOISE_GRADIENT)
                (or (interpolations interpolation) NoiseConfig/INTERPOLATE_HERMITE)
                octaves lacunarity gain normalize?))

(defn- noise-config
  "Create FBM noise function for given configuration."
  ([] (noise-config {}))
  ([cfg]
   (noise-config-obj (merge {:seed (irand)
                             :noise-type :gradient
                             :interpolation :hermite
                             :octaves 6
                             :lacunarity 2.00
                             :gain 0.5
                             :normalize? true} cfg))))

(def ^:private perlin-noise-config (noise-config {:interpolation :quintic}))
(def ^:private simplex-noise-config (noise-config {:noise-type :simplex}))
(def ^:private value-noise-config (noise-config {:noise-type :value}))

(defn vnoise
  "Value Noise.

  6 octaves, Hermite interpolation (cubic, h01)."
  {:metadoc/categories #{:noise}
   :metadoc/examples [(example-session "Usage"
                        (vnoise 3.3)
                        (vnoise 3.3 1.1)
                        (vnoise 3.3 0.0 -0.1))
                      (example-image "2d noise" "images/n/vnoise.jpg")]}
  (^double [^double x] (FBM/noise value-noise-config x))
  (^double [^double x ^double y] (FBM/noise value-noise-config x y))
  (^double [^double x ^double y ^double z] (FBM/noise value-noise-config x y z)))

(defn noise
  "Create improved Perlin Noise.

  6 octaves, quintic interpolation."
  {:metadoc/categories #{:noise}
   :metadoc/examples [(example-session "Usage"
                        (noise 3.3)
                        (noise 3.3 1.1)
                        (noise 3.3 0.0 -0.1))
                      (example-image "2d noise" "images/n/noise.jpg")]}
  (^double [^double x] (FBM/noise perlin-noise-config x))
  (^double [^double x ^double y] (FBM/noise perlin-noise-config x y))
  (^double [^double x ^double y ^double z] (FBM/noise perlin-noise-config x y z)))

(defn simplex
  "Create Simplex noise. 6 octaves."
  {:metadoc/categories #{:noise}
   :metadoc/examples [(example-session "Usage"
                        (simplex 3.3)
                        (simplex 3.3 1.1)
                        (simplex 3.3 0.0 -0.1))
                      (example-image "2d noise" "images/n/simplex.jpg")]}
  (^double [^double x] (FBM/noise simplex-noise-config x))
  (^double [^double x ^double y] (FBM/noise simplex-noise-config x y))
  (^double [^double x ^double y ^double z] (FBM/noise simplex-noise-config x y z)))

(defmacro ^:private gen-noise-function
  "Generate various noise for static function"
  [method]
  `(fn [cfg#]
     (let [ncfg# (noise-config cfg#)]
       (fn
         ([x#] (~method ncfg# x#))
         ([x# y#] (~method ncfg# x# y#))
         ([x# y# z#] (~method ncfg# x# y# z#))))))

(def ^{:metadoc/categories #{:noise}} single-noise (gen-noise-function Noise/noise))
(def ^{:metadoc/categories #{:noise}} fbm-noise (gen-noise-function FBM/noise))
(def ^{:metadoc/categories #{:noise}} billow-noise (gen-noise-function Billow/noise))
(def ^{:metadoc/categories #{:noise}} ridgedmulti-noise (gen-noise-function RidgedMulti/noise))

(defn random-noise-cfg
  "Create random noise configuration."
  {:metadoc/categories #{:noise}
   :metadoc/examples [(example "Random configuration" (random-noise-cfg))]}
  []
  {:seed (irand)
   :noise-type (rand-nth (keys noise-types))
   :interpolation (rand-nth (keys interpolations))
   :octaves (irand 1 10)
   :lacunarity (drand 1.5 2.5)
   :gain (drand 0.2 0.8)
   :normalize? true})

(defn random-noise-fn
  "Create random noise function from all possible options.

  Optionally provide own configuration `cfg`. In this case one of 4 different blending methods will be selected."
  {:metadoc/categories #{:noise}
   :metadoc/examples [(example-session "Create function"
                        (random-noise-fn)
                        (random-noise-fn (random-noise-cfg)))
                      (example-image "One" "images/n/random1.jpg")
                      (example-image "Two" "images/n/random2.jpg")
                      (example-image "Three" "images/n/random3.jpg")]}
  ([cfg]
   (rand-nth [(single-noise cfg)
              (fbm-noise cfg)
              (billow-noise cfg)
              (ridgedmulti-noise cfg)]))
  ([] (random-noise-fn (random-noise-cfg))))

(add-examples single-noise
  (example "Usage"
    (let [n (single-noise {:interpolation :linear})]
      (n 0.5 1.1 -1.3)))
  (example-image "2d noise" "images/n/single.jpg"))

(add-examples fbm-noise
  (example "Usage"
    (let [n (fbm-noise {:interpolation :linear
                        :noise-type :value})]
      (n 0.5 1.1 -1.3)))
  (example-image "2d noise" "images/n/fbm.jpg"))

(add-examples billow-noise
  (example "Usage"
    (let [n (billow-noise {:seed 12345
                           :interpolation :none})]
      (n 0.5 1.1 -1.3)))
  (example-image "2d noise" "images/n/billow.jpg"))

(add-examples ridgedmulti-noise
  (example "Usage"
    (let [n (ridgedmulti-noise {:octaves 3
                                :lacunarity 2.1
                                :gain 0.7
                                :noise-type :simplex})]
      (n 0.5 1.1 -1.3)))
  (example-image "2d noise" "images/n/ridgedmulti.jpg"))


;; ### Discrete noise

(def ^:private ^:const ^double AM (/ 2147483647.0))

(defn discrete-noise
  "Discrete noise. Parameters:

  * X (long)
  * Y (long, optional)

  Returns double value from [0,1] range"
  {:metadoc/categories #{:noise}
   :metadoc/examples [(example-session "Example calls"
                        (discrete-noise 123 444)
                        (discrete-noise 123 444)
                        (discrete-noise 123 445)
                        (discrete-noise 123))
                      (example-image "Draw noise for [0-180] range." "images/n/discrete_noise.jpg")]}
  (^double [^long X ^long Y]
   (let [X (unchecked-int X)
         Y (unchecked-int Y)
         n (unchecked-add-int X (unchecked-multiply-int Y 57))
         nn (unchecked-int (bit-xor n (<< n 13)))
         nnn (unchecked-add-int 1376312589 (unchecked-multiply-int nn (unchecked-add-int 789221 (unchecked-multiply-int nn (unchecked-multiply-int nn 15731)))))]
     (* AM (unchecked-int (bit-and 0x7fffffff nnn)))))
  (^double [^long X]
   (discrete-noise X 0)))

;; Distribution

(defprotocol DistributionProto
  "Get information from distributions."
  (^{:metadoc/categories #{:dist}} cdf [d v] [d v1 v2] "Cumulative probability.")
  (^{:metadoc/categories #{:dist}} pdf [d v] "Density")
  (^{:metadoc/categories #{:dist}} lpdf [d v] "Log density")
  (^{:metadoc/categories #{:dist}} icdf [d p] "Inversed cumulative probability")
  (^{:metadoc/categories #{:dist}} probability [d v] "Probability (PMF)")
  (^{:metadoc/categories #{:dist}} mean [d] "Mean")
  (^{:metadoc/categories #{:dist}} variance [d] "Variance")
  (^{:metadoc/categories #{:dist}} lower-bound [d] "Lower value")
  (^{:metadoc/categories #{:dist}} upper-bound [d] "Higher value")
  (^{:metadoc/categories #{:dist}} sample [d] "Returns random sample.")
  (^{:metadoc/categories #{:dist}} log-likelihood [d vs] "Log likelihood of samples")
  (^{:metadoc/categories #{:dist}} likelihood [d vs] "Likelihood of samples"))

(extend RealDistribution
  DistributionProto
  {:cdf (fn
          ([^RealDistribution d ^double v] (.cumulativeProbability d v))
          ([^RealDistribution d ^double v1 ^double v2] (.cumulativeProbability d v1 v2)))
   :pdf (fn [^RealDistribution d ^double v] (.density d v))
   :lpdf (fn [^AbstractRealDistribution d ^double v] (.logDensity d v))
   :icdf (fn [^RealDistribution d ^double p] (.inverseCumulativeProbability d p))
   :probability (fn [^RealDistribution d ^double p] (.probability d p))
   :mean (fn [^RealDistribution d] (.getNumericalMean d))
   :variance (fn [^RealDistribution d] (.getNumericalVariance d))
   :lower-bound (fn [^RealDistribution d] (.getSupportLowerBound d))
   :upper-bound (fn [^RealDistribution d] (.getSupportUpperBound d))
   :sample (fn [^RealDistribution d] (.sample d))
   :log-likelihood (fn [^RealDistribution d vs] (reduce clojure.core/+ (map #(lpdf d %) vs)))
   :likelihood #(m/exp (log-likelihood %1 %2))}
  RNGProto
  {:drandom (fn [^RealDistribution d] (.sample d))
   :frandom (fn [^RealDistribution d] (unchecked-float (.sample d)))
   :lrandom (fn [^RealDistribution d] (unchecked-long (.sample d)))
   :irandom (fn [^RealDistribution d] (unchecked-int (.sample d)))
   :->seq (fn
            ([^RealDistribution d] (repeatedly #(.sample d)))
            ([^RealDistribution d n] (repeatedly n #(.sample d))))
   :set-seed! (fn [^RealDistribution d ^double seed] (.reseedRandomGenerator d seed) d)})

(extend IntegerDistribution
  DistributionProto
  {:cdf (fn
          ([^IntegerDistribution d ^double v] (.cumulativeProbability d v))
          ([^IntegerDistribution d ^double v1 ^double v2] (.cumulativeProbability d v1 v2)))
   :icdf (fn [^IntegerDistribution d ^double p] (.inverseCumulativeProbability d p))
   :pdf (fn [^IntegerDistribution d ^double p] (.probability d p))
   :lpdf (fn [^AbstractIntegerDistribution d ^double p] (.logProbability d p))
   :probability (fn [^IntegerDistribution d ^double p] (.probability d p))
   :mean (fn [^IntegerDistribution d] (.getNumericalMean d))
   :variance (fn [^IntegerDistribution d] (.getNumericalVariance d))
   :lower-bound (fn [^IntegerDistribution d] (.getSupportLowerBound d))
   :upper-bound (fn [^IntegerDistribution d] (.getSupportUpperBound d))
   :sample (fn [^IntegerDistribution d] (.sample d))
   :log-likelihood (fn [^IntegerDistribution d vs] (reduce clojure.core/+ (map #(lpdf d %) vs)))
   :likelihood #(m/exp (log-likelihood %1 %2))}
  RNGProto
  {:drandom (fn [^IntegerDistribution d] (unchecked-double (.sample d)))
   :frandom (fn [^IntegerDistribution d] (unchecked-float (.sample d)))
   :lrandom (fn [^IntegerDistribution d] (unchecked-long (.sample d)))
   :irandom (fn [^IntegerDistribution d] (.sample d))
   :->seq (fn
            ([^IntegerDistribution d] (repeatedly #(.sample d)))
            ([^IntegerDistribution d n] (repeatedly n #(.sample d))))
   :set-seed! (fn [^IntegerDistribution d ^double seed] (.reseedRandomGenerator d seed) d)})

(defmulti
  ^{:doc "Create distribution object.

First parameter is distribution as a `:key`.
Second parameter is a map with configuration.
All distributions accept `rng` under `:rng` key (default: [[default-rng]]) and some of them accept `inverse-cumm-accuracy` (default set to `1e-9`).

Distributions should be called using [[DistributionProto]] and [[RNGProto]].

The rest parameters goes as follows:

#### Real distributions

* `:beta` - `:alpha` (default: 2.0) and `:beta` (default: 5.0)
* `:cauchy` - `:mean` (default: 0.0) and `:scale` (default: 1.0)
* `:chi-squared` - `:degrees-of-freedom` (default: 1.0)
* `:empirical` - `:bean-count` (default: 1000) and `:data` as a sequence
* `:enumerated-real` - `:data` as a sequence and `:probabilities` as a optional sequence
* `:exponential` - `:mean` (default: 1.0)
* `:f` - `:numerator-degrees-of-freedom` (default: 1.0) and `:denominator-degrees-of-freedom` (default: 1.0)
* `:gamma` - `:shape` (default: 2.0) and `:scale` (default: 2.0)
* `:gumbel` - `:mu` (default: 1.0) and `:beta` (default: 2.0)
* `:laplace` - `:mu` (default: 1.0) and `:beta` (default: 2.0)
* `:levy` - `:mu` (default: 0.0) and `:c` (default: 1.0)
* `:logistic` - `:mu` (default: 1.0) and `:s` (default: 2.0)
* `:log-normal` - `:scale` (default: 1.0) and `:shape` (default: 1.0)
* `:nakagami` - `:mu` (default: 1.0) and `:omega` (default: 1.0)
* `:normal` - `:mu` (default: 0.0) and `:sd` (default: 1.0)
* `:pareto` - `:scale` (default: 1.0) and `:shape` (default: 1.0)
* `:t` - `:degrees-of-freedom` (default: 1.0)
* `:triangular` - `:a` (default: -1.0), `:b` (default: 0.0) and `:c` (default: 1.0)
* `:uniform-real` - `:lower` (default: 0.0) and `:upper` (default: 1.0)
* `:weibull` - `:alpha` (default: 2.0) and `:beta` (default: 1.0)

#### Integer distributions

* `:binomial` - `:trials` (default: 20) and `:p` (default: 0.5)
* `:enumerated-int` - `:data` and `:probabilities` as a sequences
* `:geometric` - `:p` (default: 0.5)
* `:hypergeometric` - `:population-size` (default: 100), `:number-of-successes` (default: 50) and `:sample-size` (default: 25)
* `:pascal` - `:r` (default: 5) and `:p` (default: 0.5)
* `:poisson` - `:p` (default: 0.5), `:epsilon` (default: 1.0e-12), `:max-iterations` (default: 10000000)
* `:uniform-int` - `:lower` (default: 0) and `:upper` (default: `Integer/MAX_VALUE`)
* `:zipf` - `:number-of-elements` (default: 100) and `:exponent` (default: 3.0)
"
    :metadoc/categories #{:dist}
    :metadoc/examples [(example-session "Usage"
                         (distribution :beta)
                         (distribution :beta {:alpha 1.0 :beta 1.0}))]}
  distribution (fn ([k _] k) ([k] k)))

(defmethod distribution :beta
  ([_ {:keys [^double alpha ^double beta rng ^double inverse-cumm-accuracy]
       :or {alpha 2.0 beta 5.0 rng default-rng inverse-cumm-accuracy BetaDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (BetaDistribution. rng alpha beta inverse-cumm-accuracy))
  ([_] (distribution :beta {})))

(defmethod distribution :cauchy
  ([_ {:keys [^double mean ^double scale rng ^double inverse-cumm-accuracy]
       :or {mean 0.0 scale 1.0 rng default-rng inverse-cumm-accuracy CauchyDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (CauchyDistribution. rng mean scale inverse-cumm-accuracy))
  ([_] (distribution :cauchy {})))

(defmethod distribution :chi-squared
  ([_ {:keys [^double degrees-of-freedom rng ^double inverse-cumm-accuracy]
       :or {degrees-of-freedom 1.0 rng default-rng inverse-cumm-accuracy ChiSquaredDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (ChiSquaredDistribution. rng degrees-of-freedom inverse-cumm-accuracy))
  ([_] (distribution :chi-squared {})))

(defmethod distribution :empirical
  ([_ {:keys [^long bin-count ^RandomGenerator rng data]
       :or {bin-count EmpiricalDistribution/DEFAULT_BIN_COUNT rng default-rng}}]
   (let [^EmpiricalDistribution d (EmpiricalDistribution. bin-count rng)]
     (.load d (m/seq->double-array data))
     d))
  ([_] (distribution :empirical {})))

(defmethod distribution :enumerated-real
  ([_ {:keys [data probabilities ^RandomGenerator rng]
       :or {rng default-rng}}]
   (if probabilities
     (EnumeratedRealDistribution. rng (m/seq->double-array data) (m/seq->double-array probabilities))
     (EnumeratedRealDistribution. rng (m/seq->double-array data))))
  ([_] (distribution :enumerated-real {})))

(defmethod distribution :exponential
  ([_ {:keys [^double mean rng ^double inverse-cumm-accuracy]
       :or {mean 1 rng default-rng inverse-cumm-accuracy BetaDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (ExponentialDistribution. rng mean inverse-cumm-accuracy))
  ([_] (distribution :exponential {})))

(defmethod distribution :f
  ([_ {:keys [^double numerator-degrees-of-freedom ^double denominator-degrees-of-freedom rng ^double inverse-cumm-accuracy]
       :or {numerator-degrees-of-freedom 1.0 denominator-degrees-of-freedom 1.0 rng default-rng inverse-cumm-accuracy BetaDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (FDistribution. rng numerator-degrees-of-freedom denominator-degrees-of-freedom inverse-cumm-accuracy))
  ([_] (distribution :f {})))

(defmethod distribution :gamma
  ([_ {:keys [^double shape ^double scale rng ^double inverse-cumm-accuracy]
       :or {shape 2.0 scale 2.0 rng default-rng inverse-cumm-accuracy BetaDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (GammaDistribution. rng shape scale inverse-cumm-accuracy))
  ([_] (distribution :gamma {})))

(defmethod distribution :gumbel
  ([_ {:keys [^double mu ^double beta rng]
       :or {mu 1.0 beta 2.0 rng default-rng}}]
   (GumbelDistribution. rng mu beta))
  ([_] (distribution :gumbel {})))

(defmethod distribution :laplace
  ([_ {:keys [^double mu ^double beta rng]
       :or {mu 1.0 beta 2.0 rng default-rng}}]
   (LaplaceDistribution. rng mu beta))
  ([_] (distribution :laplace {})))

(defmethod distribution :levy
  ([_ {:keys [^double mu ^double c rng] :or {mu 0.0 c 1.0 rng default-rng}}]
   (LevyDistribution. rng mu c))
  ([_] (distribution :levy {})))

(defmethod distribution :logistic
  ([_ {:keys [^double mu ^double s rng]
       :or {mu 1.0 s 2.0 rng default-rng}}]
   (LogisticDistribution. rng mu s))
  ([_] (distribution :logistic {})))

(defmethod distribution :log-normal
  ([_ {:keys [^double scale ^double shape rng ^double inverse-cumm-accuracy]
       :or {scale 1.0 shape 1.0 rng default-rng inverse-cumm-accuracy LogNormalDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (LogNormalDistribution. rng scale shape inverse-cumm-accuracy))
  ([_] (distribution :log-normal {})))

(defmethod distribution :nakagami
  ([_ {:keys [^double mu ^double omega rng ^double inverse-cumm-accuracy]
       :or {mu 1.0 omega 1.0 rng default-rng inverse-cumm-accuracy NakagamiDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (NakagamiDistribution. rng mu omega inverse-cumm-accuracy))
  ([_] (distribution :nakagami {})))

(defmethod distribution :normal
  ([_ {:keys [^double mu ^double sd rng ^double inverse-cumm-accuracy]
       :or {mu 0.0 sd 1.0 rng default-rng inverse-cumm-accuracy NormalDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (NormalDistribution. rng mu sd inverse-cumm-accuracy))
  ([_] (distribution :normal {})))

(defmethod distribution :pareto
  ([_ {:keys [^double scale ^double shape rng ^double inverse-cumm-accuracy]
       :or {scale 1.0 shape 1.0 rng default-rng inverse-cumm-accuracy ParetoDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (ParetoDistribution. rng scale shape inverse-cumm-accuracy))
  ([_] (distribution :pareto {})))

(defmethod distribution :t
  ([_ {:keys [^double degrees-of-freedom rng ^double inverse-cumm-accuracy]
       :or {degrees-of-freedom 1.0 rng default-rng inverse-cumm-accuracy TDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (TDistribution. rng degrees-of-freedom inverse-cumm-accuracy))
  ([_] (distribution :t {})))

(defmethod distribution :triangular
  ([_ {:keys [^double a ^double b ^double c rng]
       :or {a -1.0 b 0.0 c 1.0 rng default-rng}}]
   (TriangularDistribution. rng a b c))
  ([_] (distribution :triangular {})))

(defmethod distribution :uniform-real
  ([_ {:keys [^double lower ^double upper rng]
       :or {lower 0.0 upper 1.0 rng default-rng}}]
   (UniformRealDistribution. ^RandomGenerator rng lower upper))
  ([_] (distribution :uniform-real {})))

(defmethod distribution :weibull
  ([_ {:keys [^double alpha ^double beta rng ^double inverse-cumm-accuracy]
       :or {alpha 2.0 beta 1.0 rng default-rng inverse-cumm-accuracy WeibullDistribution/DEFAULT_INVERSE_ABSOLUTE_ACCURACY}}]
   (WeibullDistribution. rng alpha beta inverse-cumm-accuracy))
  ([_] (distribution :weibull {})))

;; integer

(defmethod distribution :binomial
  ([_ {:keys [^int trials ^double p rng]
       :or {trials 20 p 0.5 rng default-rng}}]
   (BinomialDistribution. rng trials p))
  ([_] (distribution :binomial {})))

(defmethod distribution :enumerated-int
  ([_ {:keys [data probabilities ^RandomGenerator rng]
       :or {rng default-rng}}]
   (EnumeratedIntegerDistribution. rng (int-array data) (m/seq->double-array probabilities)))
  ([_] (distribution :enumerated-int {})))

(defmethod distribution :geometric
  ([_ {:keys [^double p rng]
       :or {p 0.5 rng default-rng}}]
   (GeometricDistribution. rng p))
  ([_] (distribution :geometric {})))

(defmethod distribution :hypergeometric
  ([_ {:keys [^int population-size ^int number-of-successes ^int sample-size rng]
       :or {population-size 100 number-of-successes 50 sample-size 25 rng default-rng}}]
   (HypergeometricDistribution. rng population-size number-of-successes sample-size))
  ([_] (distribution :hypergeometric {})))

(defmethod distribution :pascal
  ([_ {:keys [^int r ^double p rng]
       :or {r 5 p 0.5 rng default-rng}}]
   (PascalDistribution. rng r p))
  ([_] (distribution :pascal {})))

(defmethod distribution :poisson
  ([_ {:keys [^double p ^double epsilon ^int max-iterations rng]
       :or {p 0.5 epsilon PoissonDistribution/DEFAULT_EPSILON max-iterations PoissonDistribution/DEFAULT_MAX_ITERATIONS rng default-rng}}]
   (PoissonDistribution. rng p epsilon max-iterations))
  ([_] (distribution :poisson {})))

(defmethod distribution :uniform-int
  ([_ {:keys [^int lower ^int upper rng]
       :or {lower 0 upper Integer/MAX_VALUE rng default-rng}}]
   (UniformIntegerDistribution. rng lower upper))
  ([_] (distribution :uniform-int {})))

(defmethod distribution :zipf
  ([_ {:keys [^int number-of-elements ^double exponent rng]
       :or {number-of-elements 100 exponent 3.0 rng default-rng}}]
   (ZipfDistribution. rng number-of-elements exponent))
  ([_] (distribution :zipf {})))

(def ^{:doc "List of distributions."
       :metadoc/categories #{:dist}
       :metadoc/examples [(example-session "Number and list of distributions" distributions-list (count distributions-list))]}
  distributions-list
  (into (sorted-set) (keys (methods distribution))))

(doseq [n distributions-list]
  (add-examples distribution (example-image (str "PDFs of " (name n)) (str "images/d/" (name n) ".jpg"))))

;;

(add-examples cdf (example-session "Usage" (cdf (distribution :gamma) 1) (cdf (distribution :gamma) 1 4)))
(add-examples pdf (example "Usage" (pdf (distribution :gamma) 1)))
(add-examples lpdf (example "Usage" (lpdf (distribution :gamma) 1)))
(add-examples icdf (example "Usage" (icdf (distribution :gamma) 0.5)))
(add-examples mean (example "Usage" (mean (distribution :gamma))))
(add-examples variance (example "Usage" (variance (distribution :gamma))))
(add-examples lower-bound (example "Usage" (lower-bound (distribution :gamma))))
(add-examples upper-bound (example "Usage" (upper-bound (distribution :gamma))))
(add-examples sample (example "Random value from distribution" (sample (distribution :gamma))))
(add-examples ->seq (example "Sequence of random values from distribution" (->seq (distribution :gamma) 5)))
(add-examples log-likelihood (example "Usage" (log-likelihood (distribution :gamma) [10 0.5 0.5 1 2])))
(add-examples likelihood (example "Usage" (likelihood (distribution :gamma) [10 0.5 0.5 1 2])))

(add-examples drandom (example "Double random value from distribution" (drandom (distribution :gamma))))
(add-examples irandom (example "Integer random value from distribution (sample cast to `int`)" (irandom (distribution :gamma))))
(add-examples frandom (example "Float random value from distribution (sample cast to `float`)" (frandom (distribution :gamma))))
(add-examples lrandom (example "Long random value from distribution (sample cast to `long`)" (lrandom (distribution :gamma))))
