package clinicalnlp.pattern

import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Document
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token
import groovy.util.logging.Log4j
import org.apache.log4j.Level
import org.apache.log4j.PropertyConfigurator
import org.apache.uima.UimaContext
import org.apache.uima.analysis_engine.AnalysisEngine
import org.apache.uima.analysis_engine.AnalysisEngineProcessException
import org.apache.uima.fit.component.JCasAnnotator_ImplBase
import org.apache.uima.fit.descriptor.ConfigurationParameter
import org.apache.uima.fit.factory.AggregateBuilder
import org.apache.uima.fit.factory.AnalysisEngineFactory
import org.apache.uima.fit.pipeline.SimplePipeline
import org.apache.uima.jcas.JCas
import org.apache.uima.jcas.tcas.Annotation
import org.apache.uima.resource.ResourceInitializationException
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import java.util.regex.Matcher
import java.util.regex.Pattern

@Log4j
class AnnotationSequencerTests {

    static class TestAnnotator extends JCasAnnotator_ImplBase {

        public static final String PARAM_PATTERN = 'patternStr'
        @ConfigurationParameter(name = 'patternStr', mandatory = true,
            description = 'pattern for detecting named entities')
        private String patternStr

        Pattern pattern

        @Override
        void initialize(UimaContext context) throws ResourceInitializationException {
            super.initialize(context)
            this.pattern = Pattern.compile(this.patternStr)
        }


        @Override
        void process(JCas jcas) throws AnalysisEngineProcessException {
            Document seg = new Document(jcas)
            seg.begin = 0
            seg.end = jcas.documentText.length()
            seg.addToIndexes()

            Matcher matcher = jcas.documentText =~ /([A-Z].+\.)/
            matcher.each {
                Sentence sent = new Sentence(jcas)
                sent.begin = matcher.start(1)
                sent.end = matcher.end(1)
                sent.addToIndexes()
            }

            matcher = jcas.documentText =~ /([a-zA-Z0-9]+)/
            matcher.each {
                Token token = new Token(jcas)
                token.begin = matcher.start(1)
                token.end = matcher.end(1)
                token.addToIndexes()
            }

            matcher = jcas.documentText =~ this.pattern
            matcher.each {
                NamedEntity nem = new NamedEntity(jcas)
                nem.begin = matcher.start(1)
                nem.end = matcher.end(1)
                nem.addToIndexes()
            }
        }
    }

    JCas jcas;

    @BeforeClass
    static void setupClass() {
        Class.forName('clinicalnlp.dsl.DSL')
        def config = new ConfigSlurper().parse(
            AnnotationSequencerTests.class.getResource('/config.groovy').text)
        PropertyConfigurator.configure(config.toProperties())
    }

    @Before
    void setUp() throws Exception {
        log.setLevel(Level.INFO)

        // construct a pipeline
        AggregateBuilder builder = new AggregateBuilder()
        builder.with {
            add(AnalysisEngineFactory.createEngineDescription(
                TestAnnotator,
                TestAnnotator.PARAM_PATTERN,
                /(?i)(pneumonia|fever|cough|sepsis|weakness|measles)/))
        }
        AnalysisEngine engine = builder.createAggregate()

        // run pipeline to generate annotations
        def text = """\
        Patient has fever but no cough and pneumonia is ruled out.
        There is no increase in weakness.
        Patient does not have measles.
        """
        this.jcas = engine.newJCas()
        jcas.setDocumentText(text)
        SimplePipeline.runPipeline(jcas, engine)
    }

    @Test
    void smokeTest() {
        Collection<Document> segs = jcas.select(type:Document)
        assert segs.size() == 1
        Collection<Sentence> sents = jcas.select(type:Sentence)
        assert sents.size() == 3
        Collection<Token> tokens = jcas.select(type:Token)
        assert tokens.size() == 22
        Collection<NamedEntity> nems = jcas.select(type:NamedEntity)
        assert nems.size() == 5

        Sentence sentence = this.jcas.select(type:Sentence)[0]
        AnnotationSequencer sequencer = new AnnotationSequencer(sentence, [NamedEntity, Token])
        int iterCount = 0
        sequencer.each { List<? extends Annotation> seq ->
            String typeSeq = seq.inject('[') { prefix, next ->
                "${prefix} ${next.class.simpleName}"
            }
            println "Sequence size: ${seq.size()}; ${typeSeq} ]"
            iterCount++
        }
        assert iterCount == 8
    }

    @Test
    void testSequenceGeneration1() {
        Document newSegment = this.jcas.create(type:Document, begin:0, end:this.jcas.documentText.length())
        Collection<Class<? extends Annotation>> types = [Document]
        AnnotationSequencer sequencer = new AnnotationSequencer(newSegment, types)
        assert sequencer != null

        Iterator<List<? extends Annotation>> iter = sequencer.iterator()
        assert iter != null
        assert iter.hasNext()
        List<? extends Annotation> sequence = iter.next()
        assert sequence.size() == 1
        assert sequence[0] instanceof Document
        assert !iter.hasNext()
    }

    @Test
    void testSequenceGeneration2() {
        Document segment = this.jcas.select(type:Document)[0]
        AnnotationSequencer sequencer = new AnnotationSequencer(segment, [Sentence])
        assert sequencer != null

        Iterator<List<? extends Annotation>> iter = sequencer.iterator()
        assert iter != null
        assert iter.hasNext()
        List<? extends Annotation> sequence = iter.next()
        assert sequence.size() == 3
        assert sequence[0] instanceof Sentence
        assert sequence[1] instanceof Sentence
        assert sequence[2] instanceof Sentence
        assert !iter.hasNext()
    }

    @Test
    void testSequenceGeneration3() {
        Sentence sentence = this.jcas.select(type:Sentence)[0]
        Collection<Class<? extends Annotation>> types = [Token]
        AnnotationSequencer sequencer = new AnnotationSequencer(sentence, types)
        assert sequencer != null

        Iterator<List<? extends Annotation>> iter = sequencer.iterator()
        assert iter != null
        assert iter.hasNext()
        List<? extends Annotation> sequence = iter.next()
        assert sequence.size() == 11
        assert sequence[0] instanceof Token
        assert sequence[1] instanceof Token
        assert sequence[2] instanceof Token
        assert sequence[3] instanceof Token
        assert sequence[4] instanceof Token
        assert sequence[5] instanceof Token
        assert sequence[6] instanceof Token
        assert sequence[7] instanceof Token
        assert sequence[8] instanceof Token
        assert sequence[9] instanceof Token
        assert sequence[10] instanceof Token
        assert !iter.hasNext()
    }

    @Test
    void testSequenceGeneration4() {
        Sentence sentence = this.jcas.select(type:Sentence)[2]
        AnnotationSequencer sequencer = new AnnotationSequencer(sentence, [NamedEntity, Token])
        Iterator<List<? extends Annotation>> iter = sequencer.iterator()

        assert iter.hasNext()
        List<? extends Annotation> sequence = iter.next()
        assert sequence.size() == 5
        assert sequence[0] instanceof Token
        assert sequence[1] instanceof Token
        assert sequence[2] instanceof Token
        assert sequence[3] instanceof Token
        assert sequence[4] instanceof NamedEntity

        assert iter.hasNext()
        sequence = iter.next()
        assert sequence.size() == 5
        assert sequence[0] instanceof Token
        assert sequence[1] instanceof Token
        assert sequence[2] instanceof Token
        assert sequence[3] instanceof Token
        assert sequence[4] instanceof Token

        assert !iter.hasNext()


        // reverse order in which types are declared
        sequencer = new AnnotationSequencer(sentence, [Token, NamedEntity])
        iter = sequencer.iterator()

        assert iter.hasNext()
        sequence = iter.next()
        assert sequence.size() == 5
        assert sequence[0] instanceof Token
        assert sequence[1] instanceof Token
        assert sequence[2] instanceof Token
        assert sequence[3] instanceof Token
        assert sequence[4] instanceof Token

        assert iter.hasNext()
        sequence = iter.next()
        assert sequence.size() == 5
        assert sequence[0] instanceof Token
        assert sequence[1] instanceof Token
        assert sequence[2] instanceof Token
        assert sequence[3] instanceof Token
        assert sequence[4] instanceof NamedEntity
        assert !iter.hasNext()
    }
}
