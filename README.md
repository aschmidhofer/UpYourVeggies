# UpYourVeggies
This is an Android app showcasing image classification using TensorFlow.

It is based on TF Classify app by TensorFlow https://github.com/tensorflow/tensorflow/tree/master/tensorflow/examples/android

## The current model
The model provided was trained on the Food-11 dataset from http://mmspg.epfl.ch/food-image-datasets

## Using your own classifier 
To create your own model follow the tutorial at
https://www.tensorflow.org/tutorials/image_retraining

You can find the relevant files in the following git repository
https://github.com/tensorflow/tensorflow

After creating the model it has to be optimized. The script for this can also be found in the tensorflow git repository.
Example:

    python3 ~/git/tensorflow/tensorflow/python/tools/optimize_for_inference.py \
      --input=./mine_vH_0p15_dynamic.pb \
      --output=./optimized_mine_vH_0p15_dynamic.pb \
      --input_names="Mul" \
      --output_names="final_result"
      
To use your model add the model (*.pd) and labels files to the applications assets directory, in MainActivity.java change the path of MODEL_FILE and LABEL_FILE, and rebuild the app.
