import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor

def graphDef = new File("../feed/tensorflow-model/inception-dec-2015/tensorflow_inception_graph.pb").bytes
def labels = new File("../feed/tensorflow-model/inception-dec-2015/imagenet_comp_graph_label_strings.txt").collect { it }
def input = new File("../feed/assets/panda.jpg").bytes

def graph = new Graph()
graph.importGraphDef(graphDef)

def session = new Session(graph)

def output = session.runner()
        .feed("DecodeJpeg/contents", Tensor.create(input))
        .fetch("softmax")
        .run()
        .get(0)

Long[] shape = output.shape()

assert output.numDimensions() == 2
assert shape.size() == 2

def result = new float[1][shape.last()]

output.copyTo(result)

probabilities = []
result[0].eachWithIndex{ float entry, int i -> probabilities[i] = [i, entry] }
probabilities.sort { it[1] }

println "Top 5:"
probabilities[-1..-5].each { it ->
    println "${labels[it[0]].padLeft(12)}:${(it[1] * 100).round(2)}%"
}


println """
Most likely:
${labels[probabilities[-1][0]]}
"""
