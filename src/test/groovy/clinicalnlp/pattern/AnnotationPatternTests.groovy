package clinicalnlp.pattern

import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import static clinicalnlp.pattern.AnnotationPattern.*

class AnnotationPatternTests {

    @BeforeClass
    static void setupClass() {
    }

    @Before
    void setUp() throws Exception {
    }

    @Test
    void testAtomicPatterns() {
        AnnotationPattern pattern1 = $A(Token, [posValue:'NN', text:'/Foo/'])
        assert pattern1 != null
        assert pattern1.type == Token
        assert pattern1.features == [posValue:'NN', text:'/Foo/']
        assert pattern1.range == null
        assert pattern1.name == null

        String patternStr = pattern1.toString()
        println patternStr

        AnnotationPattern pattern2 = $N('t1', pattern1(0,3))
        assert pattern2 != null
        assert pattern2 == pattern1
        assert pattern2.range == (0..3)
        assert pattern2.name == 't1'
    }

    @Test
    void testSequencePatterns() {
        AnnotationPattern pattern1 = $A(Token, [posValue:'NN', text:'/Foo/'])
        AnnotationPattern pattern2 = $A(Token, [posValue:'VB', text:'/Bar/'])
        AnnotationPattern pattern3 = $A(NamedEntity, [identifier:'C01'])
        AnnotationPattern pattern4 = pattern1 & pattern2 & pattern3
        assert pattern4 != null
        assert pattern4 instanceof SequenceAnnotationPattern
        assert pattern4.children.size() == 3
        assert pattern4.children[0] == pattern1
        assert pattern4.children[1] == pattern2
        assert pattern4.children[2] == pattern3

        AnnotationPattern pattern5 = pattern1 & (pattern2 & pattern3)
        assert pattern5 != null
        assert pattern5 instanceof SequenceAnnotationPattern
        assert pattern5.children.size() == 2
        assert pattern5.children[0] == pattern1
        assert pattern5.children[1] instanceof SequenceAnnotationPattern
        assert pattern5.children[1].children[0] == pattern2
        assert pattern5.children[1].children[1] == pattern3

        pattern5 = $N('seq1', pattern5)
        assert pattern5.name == 'seq1'

        AnnotationPattern pattern6 =
            $N('seq1', $A(Token, [posValue:'NN', text:'/Foo/']) &
                $N('seq2', $A(Token, [posValue:'VB', text:'/Bar/']) &
                    $A(NamedEntity, [identifier:'C01'])))
        assert pattern6 != null
        assert pattern6 instanceof SequenceAnnotationPattern
        assert pattern6.name == 'seq1'
        assert pattern6.children.size() == 2
        assert pattern6.children[1].name == 'seq2'
        assert pattern6.children[1].children.size() == 2
    }

    @Test
    void testOptionPatterns() {
        AnnotationPattern pattern1 = $A(Token, [posValue:'NN', text:'/Foo/'])
        AnnotationPattern pattern2 = $A(Token, [posValue:'VB', text:'/Bar/'])
        AnnotationPattern pattern3 = $A(NamedEntity, [identifier:'C01'])
        AnnotationPattern pattern4 = $N('opts', pattern1 | pattern2 | pattern3)
        assert pattern4 != null
        assert pattern4 instanceof OptionAnnotationPattern
        assert pattern4.children.size() == 3
        assert pattern4.children[0] == pattern1
        assert pattern4.children[1] == pattern2
        assert pattern4.children[2] == pattern3

        AnnotationPattern pattern5 = (pattern1 | (pattern2 | pattern3))
        assert pattern5 != null
        assert pattern5 instanceof OptionAnnotationPattern
        assert pattern5.children.size() == 2
        assert pattern5.children[0] == pattern1
        assert pattern5.children[1] instanceof OptionAnnotationPattern
        assert pattern5.children[1].children[0] == pattern2
        assert pattern5.children[1].children[1] == pattern3
    }

    @Test
    void testMixedPatterns() {
        AnnotationPattern p1 = $A(Token, [posValue:'NN', text:'/Foo/'])
        AnnotationPattern p2 = $A(Sentence, [text:'/The coffee is great./'])
        AnnotationPattern p3 = $A(NamedEntity, [identifier:'C01'])

        def pattern = p1 | p2 & p3
        assert pattern instanceof OptionAnnotationPattern
        assert pattern.children[0] instanceof AtomicAnnotationPattern
        assert pattern.children[1] instanceof SequenceAnnotationPattern

        pattern = (p1 | p2) & p3
        assert pattern instanceof SequenceAnnotationPattern
        assert pattern.children[0] instanceof OptionAnnotationPattern
        assert pattern.children[1] instanceof AtomicAnnotationPattern

        pattern = p1 & p2 & p3 & p1 | p2 & p3
        assert pattern instanceof OptionAnnotationPattern
        assert pattern.children.size() == 2
        assert pattern.children[0] instanceof SequenceAnnotationPattern
        assert pattern.children[0].children.size() == 4
        assert pattern.children[1] instanceof SequenceAnnotationPattern
        assert pattern.children[1].children.size() == 2

        pattern = p1 & p2 & p3 & (p1 | p2) & p3
        assert pattern instanceof SequenceAnnotationPattern
        assert pattern.children.size() == 5
        assert pattern.children[0] instanceof AtomicAnnotationPattern
        assert pattern.children[1] instanceof AtomicAnnotationPattern
        assert pattern.children[2] instanceof AtomicAnnotationPattern
        assert pattern.children[3] instanceof OptionAnnotationPattern
        assert pattern.children[4] instanceof AtomicAnnotationPattern

        pattern =
            $A(Token) & $A(Sentence) & $A(NamedEntity) &
                ($A(Token)|$A(Sentence)) & $A(NamedEntity)
        assert pattern instanceof SequenceAnnotationPattern
        assert pattern.children.size() == 5
        assert pattern.children[0] instanceof AtomicAnnotationPattern
        assert pattern.children[1] instanceof AtomicAnnotationPattern
        assert pattern.children[2] instanceof AtomicAnnotationPattern
        assert pattern.children[3] instanceof OptionAnnotationPattern
        assert pattern.children[4] instanceof AtomicAnnotationPattern
    }

    @Test
    void testLookArounds() {

        AnnotationPattern p1 = $A(Token, [posValue:'NN', text:'Coffee'])
        AnnotationPattern p2 = $A(Token, [posValue:'VB', text:'tastes'])
        AnnotationPattern p3 = $A(Token, [posValue:'JJ', text:'great'])

        AnnotationPattern pattern = +$LB(p1) & p2 & -$LA(p3)
        assert pattern instanceof SequenceAnnotationPattern
        assert p1.lookBehind == true
        assert p1.positive == true
        assert p3.lookAhead == true
        assert p3.positive == false
    }
}
