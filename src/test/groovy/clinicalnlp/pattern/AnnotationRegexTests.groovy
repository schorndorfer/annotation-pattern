package clinicalnlp.pattern

import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token
import groovy.util.logging.Log4j
import org.apache.log4j.Level
import org.apache.log4j.PropertyConfigurator
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import static clinicalnlp.pattern.AnnotationPattern.*

@Log4j
class AnnotationRegexTests {

    @BeforeClass
    static void setupClass() {
        def config = new ConfigSlurper().parse(
            AnnotationRegexTests.class.getResource('/config.groovy').text)
        PropertyConfigurator.configure(config.toProperties())
    }

    @Before
    void setUp() throws Exception {
        log.setLevel(Level.INFO)
    }

    @Test
    void testAtomicPatterns() {
        // create an atom
        AnnotationPattern pattern = $A(Token, [posValue:'NN', shape:'all_caps', text:/Foo/])
        AnnotationRegex regex = new AnnotationRegex(pattern)
        assert regex.pattern.toString() == '(?:①‹(?:NN)›‹(?:all_caps)›‹(?:Foo)›)'

        // add quantifier
        pattern = (pattern(0,3))
        regex = new AnnotationRegex(pattern)
        assert regex.pattern.toString() == '(?:(?:①‹(?:NN)›‹(?:all_caps)›‹(?:Foo)›){0,3})'

        // add a name
        pattern = $N('n1', pattern)
        regex = new AnnotationRegex(pattern)
        assert regex.pattern.toString() == '(?:(?<n1>(?:①‹(?:NN)›‹(?:all_caps)›‹(?:Foo)›){0,3}))'
    }

    @Test
    void testSequencePatterns() {
        // create some atoms
        AnnotationPattern pattern1 = $A(Token, [posValue:'NN', text:/.+/])
        AnnotationPattern pattern2 = $A(Sentence, [text:/(?i)Bar/])
        AnnotationPattern pattern3 = $A(NamedEntity, [text:/Bar/])
        AnnotationPattern pattern4 = $A(Token, [text:/Baz/])

        // assemble a complex pattern
        AnnotationPattern pattern = (pattern1&pattern2&pattern3&pattern2&pattern4)
        pattern = $N('group1', pattern(0,3))
        AnnotationRegex regex = new AnnotationRegex(pattern)
        assert regex.pattern.toString() ==
            '(?:(?<group1>(?:(?:①‹(?:NN)›‹(?:[^‹›]+)›)(?:②‹(?:(?i)Bar)›)(?:③‹(?:Bar)›)(?:②‹(?:(?i)Bar)›)(?:①‹(?:[^‹›]*)›‹(?:Baz)›)){0,3}))'
    }

    @Test
    void testOptionPatterns() {
        // create some atoms
        AnnotationPattern pattern1 = $A(Token, [text:/Foo/])
        AnnotationPattern pattern2 = $A(Sentence, [text:/Bar/])
        AnnotationPattern pattern3 = $A(NamedEntity, [text:/Bar/])

        // assemble a complex pattern
        AnnotationPattern pattern = (pattern1|pattern2|pattern3|pattern2|pattern1)
        AnnotationRegex regex = new AnnotationRegex(pattern)
        assert regex.pattern.toString() ==
            '(?:(?:①‹(?:Foo)›)|(?:②‹(?:Bar)›)|(?:③‹(?:Bar)›)|(?:②‹(?:Bar)›)|(?:①‹(?:Foo)›))'
    }

    @Test
    void testMixedPatterns() {
        // create some atoms
        AnnotationPattern pattern1 = $A(Token, [posValue:'NN'])
        AnnotationPattern pattern2 = $A(Sentence, [modality:'Assertion'])
        AnnotationPattern pattern3 = $A(NamedEntity, [type:'Disease'])
        AnnotationPattern pattern = (pattern1&pattern2|pattern3&pattern2|pattern1)

        // assemble a complex pattern
        AnnotationRegex regex = new AnnotationRegex(pattern)
        assert regex.pattern.toString() ==
            '(?:(?:(?:①‹(?:NN)›)(?:②‹(?:Assertion)›))|(?:(?:③‹(?:Disease)›)(?:②‹(?:Assertion)›))|(?:①‹(?:NN)›))'

        // assemble a complex pattern
        pattern = (pattern1&(pattern2|pattern3)&pattern2|pattern1)
        regex = new AnnotationRegex(pattern)
        assert regex.pattern.toString() ==
            '(?:(?:(?:①‹(?:NN)›)(?:(?:②‹(?:Assertion)›)|(?:③‹(?:Disease)›))(?:②‹(?:Assertion)›))|(?:①‹(?:NN)›))'

        // assemble a complex pattern
        pattern =
            ($A(Token, [posValue:'NN']) &
                ($A(Sentence, [modality:'Assertion']) | $A(NamedEntity, [type:'Disease'])) &
                $A(Sentence, [modality:'Assertion']) |
                $A(Token, [posValue:'NN']))
        assert (new AnnotationRegex(pattern)).pattern.toString() ==
            '(?:(?:(?:①‹(?:NN)›)(?:(?:②‹(?:Assertion)›)|(?:③‹(?:Disease)›))(?:②‹(?:Assertion)›))|(?:①‹(?:NN)›))'
    }
}
