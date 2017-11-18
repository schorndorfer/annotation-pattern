package textractor.pattern

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Document
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Paragraph
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Stem
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

import static textractor.pattern.AnnotationPattern.*
import static textractor.AnnotationHelper.*

@Log4j
class AnnotationMatcherTests {

    static class TestAnnotator extends JCasAnnotator_ImplBase {
        @Override
        void process(JCas jcas) throws AnalysisEngineProcessException {
            String text = jcas.documentText
            create(jcas, [type: Document, begin: 0, end: text.length()])
            create(jcas, [type: Sentence, begin: 0, end: text.length()])
            Matcher m = (text =~ /\b\w+\b/)
            m.each {
                Token t = create(jcas, [type: Token, begin: m.start(0), end: m.end(0)])
                switch (t.coveredText) {
                    case 'Tubular':
                        t.pos = create(jcas, [type:POS, posValue:'JJ'])
                        t.lemma = create(jcas, [type:Lemma, value:'tub'])
                        t.stem = create(jcas, [type:Stem, value:'Tub'])
                        break
                    case 'adenoma':
                        t.pos = create(jcas, [type:POS, posValue:'NN'])
                        break
                    case 'was':
                        t.pos = create(jcas, [type:POS, posValue:'AUX'])
                        t.lemma = create(jcas, [type:Lemma, value:'is'])
                        break
                    case 'seen':
                        t.pos = create(jcas, [type:POS, posValue:'VBN'])
                        t.lemma = create(jcas, [type:Lemma, value:'see'])
                        t.stem = create(jcas, [type:Stem, value:'see'])
                        break
                    case 'in':
                        t.pos = create(jcas, [type:POS, posValue:'IN'])
                        t.lemma = create(jcas, [type:Lemma, value:'in'])
                        break
                    case 'the':
                        t.pos = create(jcas, [type:POS, posValue:'DT'])
                        t.lemma = create(jcas, [type:Lemma, value:'the'])
                        break
                    case 'sigmoid':
                        t.pos = create(jcas, [type:POS, posValue:'JJ'])
                        break
                    case 'colon':
                        t.pos = create(jcas, [type:POS, posValue:'NN'])
                        break
                    case '.':
                        t.pos = create(jcas, [type:POS, posValue:'PUNC'])
                        break
                }
            }
            m = (text =~ /(?i)\b(sigmoid\s+colon)|(tubular\s+adenoma)|(polyps)\b/)
            m.each {
                NamedEntity nem = create(jcas, [type: NamedEntity, begin: m.start(0), end: m.end(0)])
                switch (nem.coveredText) {
                    case 'Tubular adenoma': nem.value = 'C01'; break
                    case 'sigmoid colon': nem.value = 'C02'; break
                    case 'polyps': nem.value = 'C03'; break
                }
            }
        }
    }

    @BeforeClass static void setupClass() {
        def config = new ConfigSlurper().parse(
            AnnotationMatcherTests.class.getResource('/config.groovy').text)
        PropertyConfigurator.configure(config.toProperties())
    }

    JCas jcas;

    @Before void setUp() throws Exception {
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

    @Test void testMatch1() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $N('tokens', $A(Token)(1,3)))

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testMatch2() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $N('finding', $A(NamedEntity, [text:/(?i)tubular\s+adenoma/])) &
                $A(Token)(0,2) &
                $N('seen', $A(Token, [text:/seen/, posValue:/V.*/])) &
                $N('tokens', $A(Token, [text:/in|the/])(0,2)) &
                $N('site', $A(NamedEntity, [text:/(?i)Sigmoid\s+colon/, value:/C.2/]))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0],
            [NamedEntity, Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        assert binding != null
        assert !matcher.hasNext()
        NamedEntity finding = binding.getVariable('finding')[0]
        assert finding != null
        assert finding.coveredText ==~ /(?i)tubular\s+adenoma/
        NamedEntity site = binding.getVariable('site')[0]
        assert site != null
        assert site.coveredText ==~ /sigmoid\s+colon/
        Token token = binding.getVariable('tokens')[0]
        assert token != null
        assert token.coveredText == 'in'
        token = binding.getVariable('tokens')[1]
        assert token != null
        assert token.coveredText == 'the'
    }

    @Test void testGroups() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $N('finding',
                $A(NamedEntity, [text:/(?i)tubular\s+adenoma/]) &
                $A(Token)(1,5)) &
                $N('site',
                    $A(Token)(1,3) & $A(NamedEntity))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0],
            [NamedEntity, Token])
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

    @Test void testUnions() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            $A(Token)(0,5) &
                $N('nem',
                    $A(NamedEntity, [text:/(?i)tubular\s+adenoma/]) |
                        $A(NamedEntity, [text:/(?i)sigmoid\s+colon/])) &
                $A(Token)(0,5)
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0],
            [NamedEntity, Token])
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

    @Test void testPositiveLookAhead() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex(
            $N('tok', $A(Token)) & $N('adenoma', $A(Token, [text:/adenoma/]))>>true
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testNegativeLookAhead() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex(
            $N('tok', $A(Token)) & $A(Token, [text:/adenoma|seen|sigmoid|colon/])>>false
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testPositiveLookBehind() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $A(Token, [text:/adenoma/])<<true & $N('tok', $A(Token)(3,3))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testNegativeLookBehind() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $A(Token, [text:/adenoma|seen|sigmoid/])<<false & $N('tok', $A(Token))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testLookAround() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $A(Token, [text:/was/])<<true &
                $N('tok', $A(Token)(0,3)) &
                $A(Token, [text:/sigmoid/])>>true
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testLazyQuantifier() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            $N('tokens', $A(Token)(3,5, false)) // 'false' -> greedy is false
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testTextSpan() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        //noinspection GroovyAssignabilityCheck
        AnnotationRegex regex = new AnnotationRegex(
            +$LB($A(NamedEntity, [value:'C01'])) &
                $N('tok', $A(Token)(0,10)) &
                +$LA($A(NamedEntity, [value:'C02']))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0],
            [NamedEntity, Token])
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
        create(jcas, [type:Paragraph, begin:tok[0].begin, end:tok[3].end])
        assert select(jcas, [type:Paragraph]).size() == 1

        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance that looks for the TextSpan
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex2 = new AnnotationRegex(
            $N('span', $A(Paragraph))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Paragraph])
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

    @Test void testWildcards() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $N('tokens', $A(Token, [posValue:/(N|V|D).+/, lemmaValue:/.+/, stemValue:/.*/, text:/.+/]))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testNamedGroups1() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $N('nem', $A(NamedEntity) & $N('tok', $A(Token)))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0],
            [NamedEntity, Token])
        AnnotationRegexMatcher matcher = regex.matcher(sequencer.first())

        //--------------------------------------------------------------------------------------------------------------
        // Validate the matches
        //--------------------------------------------------------------------------------------------------------------
        assert matcher.hasNext()
        Binding binding = matcher.next()
        List<? extends Annotation> nem = binding.getVariable('nem')
        assert nem.size() == 2
        assert nem[0] instanceof NamedEntity
        assert nem[1] instanceof Token
        List<? extends Annotation> tok = binding.getVariable('tok')
        assert tok.size() == 1
        assert tok[0] instanceof Token
        assert nem[1] == tok[0]
    }

    @Test void testNamedGroups2() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $N('tokens', $N('tok1', $A(Token)) & $N('tok2', $A(Token)) & $N('tok3', $A(Token)))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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

    @Test void testNamedGroups3() {
        //--------------------------------------------------------------------------------------------------------------
        // Create an AnnotationRegex instance
        //--------------------------------------------------------------------------------------------------------------
        AnnotationRegex regex = new AnnotationRegex((AnnotationPattern)
            $N('tokens',
                $N('tok1', $A(Token, [text:/Tubul.*/])) |
                $N('tok2', $A(Token, [text:/aden.../])) |
                $N('tok3',
                    $N('sigmoid', $A(Token, [text:/...moid/])) &
                    $N('colon', $A(Token, [text:/(?i)COLON(IC)?/]))))
        )

        //--------------------------------------------------------------------------------------------------------------
        // Create a sequence of annotations and a matcher
        //--------------------------------------------------------------------------------------------------------------
        AnnotationSequencer sequencer = new AnnotationSequencer(select(jcas, [type:Sentence])[0], [Token])
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
