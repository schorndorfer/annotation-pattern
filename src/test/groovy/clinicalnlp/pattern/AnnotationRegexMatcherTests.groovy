package clinicalnlp.pattern

import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Document
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token
import groovy.util.logging.Log4j
import org.apache.log4j.Level
import org.apache.log4j.PropertyConfigurator
import org.apache.uima.analysis_engine.AnalysisEngine
import org.apache.uima.analysis_engine.AnalysisEngineProcessException
import org.apache.uima.fit.component.JCasAnnotator_ImplBase
import org.apache.uima.fit.factory.AggregateBuilder
import org.apache.uima.fit.factory.AnalysisEngineFactory
import org.apache.uima.fit.pipeline.SimplePipeline
import org.apache.uima.jcas.JCas
import org.apache.uima.jcas.tcas.Annotation
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import java.util.regex.Matcher

@Log4j
class AnnotationRegexMatcherTests {

    /**
     * TestAnnotator
     */
    static class TestAnnotator extends JCasAnnotator_ImplBase {
        @Override
        void process(JCas jcas) throws AnalysisEngineProcessException {
            String text = jcas.documentText
            jcas.create(type: Document, begin: 0, end: text.length())
            jcas.create(type: Sentence, begin: 0, end: text.length())
            Matcher m = (text =~ /\b\w+\b/)
            m.each {
                Token t = jcas.create(type: Token, begin: m.start(0), end: m.end(0))
                switch (t.coveredText) {
                    case 'Tubular': t.pos = 'JJ'; t.lemma = 'tube'; t.stem = 'Tub'; break;
                    case 'adenoma': t.pos = 'NN'; break;
                    case 'was': t.pos = 'AUX'; t.lemma = 'is'; break;
                    case 'seen': t.pos = 'VBN'; t.lemma = 'see'; t.stem = 'see'; break;
                    case 'in': t.pos = 'IN'; t.lemma = 'in'; break;
                    case 'the': t.pos = 'DT'; t.lemma = 'the'; break;
                    case 'sigmoid': t.pos = 'JJ'; break;
                    case 'colon': t.pos = 'NN'; break;
                    case '.': t.pos = 'PUNC'; break;
                }
            }
            m = (text =~ /(?i)\b(sigmoid\s+colon)|(tubular\s+adenoma)|(polyps)\b/)
            m.each {
                NamedEntity nem = jcas.create(type: NamedEntity, begin: m.start(0), end: m.end(0))
                switch (nem.coveredText) {
                    case 'Tubular adenoma': nem.code = 'C01'; break;
                    case 'sigmoid colon': nem.code = 'C02'; break;
                    case 'polyps': nem.code = 'C03'; break;
                }
            }
        }
    }

    @BeforeClass
    static void setupClass() {
        def config = new ConfigSlurper().parse(
            AnnotationRegexMatcherTests.class.getResource('/config.groovy').text)
        PropertyConfigurator.configure(config.toProperties())
        Class.forName('clinicalnlp.dsl.DSL')
    }

    JCas jcas;

    @Before
    void setUp() throws Exception {
        log.setLevel(Level.INFO)
        AggregateBuilder builder = new AggregateBuilder()
        builder.with {
            add(AnalysisEngineFactory.createEngineDescription(TestAnnotator))
        }
        AnalysisEngine engine = builder.createAggregate()
        String text = 'Tubular adenoma was seen in the sigmoid colon'
        this.jcas = engine.newJCas()
        this.jcas.setDocumentText(text)
        SimplePipeline.runPipeline(this.jcas, engine)
    }

    @Test
    void testMatch1() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(AnnotationPattern.get$N('tokens', AnnotationPattern.set$A(Token)(1,3)))

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        matcher.each { Binding binding ->
            assert binding.hasVariable('tokens')
        }
        // create a new matcher to start over
        matcher = regex.matcher(sequencer.first())
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<Token> tokens = binding.getVariable('tokens')
        assert tokens.size() == 3
        assert tokens[0].coveredText == 'Tubular'
        assert tokens[1].coveredText == 'adenoma'
        assert tokens[2].coveredText == 'was'
        assert matcher.hasNext()
        binding = matcher.next()
        tokens = binding.getVariable('tokens')
        assert tokens.size() == 3
        assert tokens[0].coveredText == 'seen'
        assert tokens[1].coveredText == 'in'
        assert tokens[2].coveredText == 'the'
        assert matcher.hasNext()
        binding = matcher.next()
        tokens = binding.getVariable('tokens')
        assert tokens.size() == 2
        assert tokens[0].coveredText == 'sigmoid'
        assert tokens[1].coveredText == 'colon'
        assert !matcher.hasNext()
    }

    @Test
    void testMatch2() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('finding', AnnotationPattern.get$A(NamedEntityMention, [text:/(?i)tubular\s+adenoma/])) &
                AnnotationPattern.set$A(Token)(0,2) &
                AnnotationPattern.get$N('seen', AnnotationPattern.get$A(Token, [text:/seen/, pos:/V.*/])) &
                AnnotationPattern.get$N('tokens', AnnotationPattern.get$A(Token, [text:/in|the/])(0,2)) &
                AnnotationPattern.get$N('site', AnnotationPattern.get$A(NamedEntityMention, [text:/(?i)Sigmoid\s+colon/, code:/C.2/]))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0],
            [NamedEntityMention, Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        assert binding != null
        assert !matcher.hasNext()
        NamedEntityMention finding = binding.getVariable('finding')[0]
        assert finding != null
        assert finding.coveredText ==~ /(?i)tubular\s+adenoma/
        NamedEntityMention site = binding.getVariable('site')[0]
        assert site != null
        assert site.coveredText ==~ /sigmoid\s+colon/
        Token token = binding.getVariable('tokens')[0]
        assert token != null
        assert token.coveredText == 'in'
        token = binding.getVariable('tokens')[1]
        assert token != null
        assert token.coveredText == 'the'
    }

    @Test
    void testGroups() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('finding',
                AnnotationPattern.get$A(NamedEntityMention, [text:/(?i)tubular\s+adenoma/]) &
                AnnotationPattern.set$A(Token)(1,5)) &
                AnnotationPattern.get$N('site',
                    AnnotationPattern.set$A(Token)(1,3) & AnnotationPattern.set$A(NamedEntityMention))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0],
            [NamedEntityMention, Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        assert !matcher.hasNext()
        List<? extends Annotation> finding = binding.getVariable('finding')
        assert finding.size() == 4
        assert finding[0].coveredText == 'Tubular adenoma'
        assert finding[1].coveredText == 'was'
        assert finding[2].coveredText == 'seen'
        assert finding[3].coveredText == 'in'
        List<? extends Annotation> site = binding.getVariable('site')
        assert site.size() == 2
        assert site[0].coveredText == 'the'
        assert site[1].coveredText == 'sigmoid colon'
    }

    @Test
    void testUnions() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.set$A(Token)(0,5) &
                AnnotationPattern.get$N('nem',
                    AnnotationPattern.get$A(NamedEntityMention, [text:/(?i)tubular\s+adenoma/]) |
                        AnnotationPattern.get$A(NamedEntityMention, [text:/(?i)sigmoid\s+colon/])) &
                AnnotationPattern.set$A(Token)(0,5)
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0],
            [NamedEntityMention, Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> nem = binding.getVariable('nem')
        assert nem.size() == 1
        assert nem[0].coveredText == 'Tubular adenoma'
        binding = matcher.next()
        nem = binding.getVariable('nem')
        assert nem.size() == 1
        assert nem[0].coveredText == 'sigmoid colon'
        assert !matcher.hasNext()
    }

    @Test
    void testPositiveLookAhead() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('tok', AnnotationPattern.set$A(Token)) & AnnotationPattern.get$N('adenoma', AnnotationPattern.get$A(Token, [text:/adenoma/]))>>true
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'Tubular'
        assert !matcher.hasNext()
    }

    @Test
    void testNegativeLookAhead() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('tok', AnnotationPattern.set$A(Token)) & AnnotationPattern.get$A(Token, [text:/adenoma|seen|sigmoid|colon/])>>false
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'adenoma'
        binding = matcher.next()
        tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'seen'
        binding = matcher.next()
        tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'in'
        binding = matcher.next()
        tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'colon'
        assert !matcher.hasNext()
    }

    @Test
    void testPositiveLookBehind() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$A(Token, [text:/adenoma/])<<true & AnnotationPattern.get$N('tok', AnnotationPattern.set$A(Token)(3,3))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> tok = binding.getVariable('tok')
        assert tok.size() == 3
        assert tok[0].coveredText == 'was'
        assert tok[1].coveredText == 'seen'
        assert tok[2].coveredText == 'in'
        assert !matcher.hasNext()
    }

    @Test
    void testNegativeLookBehind() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$A(Token, [text:/adenoma|seen|sigmoid/])<<false & AnnotationPattern.get$N('tok', AnnotationPattern.set$A(Token))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'Tubular'
        binding = matcher.next()
        tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'adenoma'
        binding = matcher.next()
        tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'seen'
        binding = matcher.next()
        tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'the'
        binding = matcher.next()
        tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0].coveredText == 'sigmoid'
        assert !matcher.hasNext()
    }

    @Test
    void testLookAround() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$A(Token, [text:/was/])<<true &
                AnnotationPattern.get$N('tok', AnnotationPattern.set$A(Token)(0,3)) &
                AnnotationPattern.get$A(Token, [text:/sigmoid/])>>true
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> tok = binding.getVariable('tok')
        assert tok.size() == 3
        assert tok[0].coveredText == 'seen'
        assert tok[1].coveredText == 'in'
        assert tok[2].coveredText == 'the'
        assert !matcher.hasNext()
    }

    @Test
    void testLazyQuantifier() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('tokens', AnnotationPattern.set$A(Token)(3,5, false)) // 'false' -> greedy is false
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        def tokens = binding.getVariable('tokens')
        assert tokens.size() == 3
        assert matcher.hasNext()
        binding = matcher.next()
        tokens = binding.getVariable('tokens')
        assert tokens.size() == 3
        assert !matcher.hasNext()
    }

    @Test
    void testTextSpan() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            +AnnotationPattern.set$LB(AnnotationPattern.get$A(NamedEntityMention, [code:'C01'])) &
                AnnotationPattern.get$N('tok', AnnotationPattern.set$A(Token)(0,10)) &
                +AnnotationPattern.set$LA(AnnotationPattern.get$A(NamedEntityMention, [code:'C02']))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0],
            [NamedEntityMention, Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> tok = binding.getVariable('tok')
        assert tok.size() == 4
        assert tok[0].coveredText == 'was'
        assert tok[1].coveredText == 'seen'
        assert tok[2].coveredText == 'in'
        assert tok[3].coveredText == 'the'
        jcas.create(type:TextSpan, begin:tok[0].begin, end:tok[3].end)
        assert jcas.select(type:TextSpan).size() == 1

        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance that looks for the TextSpan
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex2 = new AnnotationRegex(
            AnnotationPattern.get$N('span', AnnotationPattern.set$A(TextSpan))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [TextSpan])
        matcher = regex2.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        binding = matcher.next()
        def span = binding.getVariable('span')
        assert span.size() == 1
        assert span[0].coveredText == 'was seen in the'
        assert !matcher.hasNext()
    }

    @Test
    void testWildcards() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('tokens', AnnotationPattern.get$A(Token, [pos:/(N|V|D).+/, lemma:/.+/, stem:/.*/, text:/.+/]))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> tok = binding.getVariable('tokens')
        tok = binding.getVariable('tokens')
        assert tok.size() == 1
        assert tok[0].coveredText == 'seen'
        binding = matcher.next()
        tok = binding.getVariable('tokens')
        assert tok.size() == 1
        assert tok[0].coveredText == 'the'
        assert !matcher.hasNext()
    }

    @Test
    void testNamedGroups1() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('nem', AnnotationPattern.set$A(NamedEntityMention) & AnnotationPattern.get$N('tok', AnnotationPattern.set$A(Token)))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0],
            [NamedEntityMention, Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> nem = binding.getVariable('nem')
        assert nem.size() == 2
        assert nem[0] instanceof NamedEntityMention
        assert nem[1] instanceof Token
        List<? extends Annotation> tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0] instanceof Token
        assert nem[1] == tok[0]
    }

    @Test
    void testNamedGroups2() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('tokens', AnnotationPattern.get$N('tok1', AnnotationPattern.set$A(Token)) & AnnotationPattern.get$N('tok2', AnnotationPattern.set$A(Token)) & AnnotationPattern.get$N('tok3', AnnotationPattern.set$A(Token)))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        def tok1 = binding.getVariable('tok1')
        assert tok1.size() == 1
        def tok2 = binding.getVariable('tok2')
        assert tok2.size() == 1
        def tok3 = binding.getVariable('tok3')
        assert tok3.size() == 1
        def tokens = binding.getVariable('tokens')
        assert tokens.size() == 3
        assert tokens[0] == tok1[0]
        assert tokens[1] == tok2[0]
        assert tokens[2] == tok3[0]
     }

    @Test
    void testNamedGroups3() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            AnnotationPattern.get$N('tokens',
                AnnotationPattern.get$N('tok1', AnnotationPattern.get$A(Token, [text:/Tubul.*/])) |
                AnnotationPattern.get$N('tok2', AnnotationPattern.get$A(Token, [text:/aden.../])) |
                AnnotationPattern.get$N('tok3',
                    AnnotationPattern.get$N('sigmoid', AnnotationPattern.get$A(Token, [text:/...moid/])) &
                    AnnotationPattern.get$N('colon', AnnotationPattern.get$A(Token, [text:/(?i)COLON(IC)?/]))))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(jcas.select(type:Sentence)[0], [Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        Binding binding = matcher.next()
        def tok = binding.getVariable('tok1')
        assert tok.size() == 1
        assert !binding.hasVariable('tok2')
        assert !binding.hasVariable('tok3')
        def tokens = binding.getVariable('tokens')
        assert tokens.size() == 1
        assert tokens[0] == tok[0]

        binding = matcher.next()
        tok = binding.getVariable('tok2')
        assert tok.size() == 1
        assert !binding.hasVariable('tok1')
        assert !binding.hasVariable('tok3')
        tokens = binding.getVariable('tokens')
        assert tokens.size() == 1
        assert tokens[0] == tok[0]

        binding = matcher.next()
        tok = binding.getVariable('tok3')
        assert tok.size() == 2
        assert binding.getVariable('sigmoid').size() == 1
        assert binding.getVariable('colon').size() == 1
        assert !binding.hasVariable('tok1')
        assert !binding.hasVariable('tok2')
        tokens = binding.getVariable('tokens')
        assert tokens.size() == 2
        assert tokens[0] == binding.getVariable('sigmoid')[0]
        assert tokens[1] == binding.getVariable('colon')[0]

        assert !matcher.hasNext()
    }

}