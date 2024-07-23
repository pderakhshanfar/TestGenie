package org.jetbrains.research.testspark.kotlin

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.research.testspark.langwrappers.PsiClassWrapper
import org.jetbrains.research.testspark.langwrappers.PsiMethodWrapper

class KotlinPsiMethodWrapper(val psiFunction: KtFunction) : PsiMethodWrapper {

    override val name: String get() = psiFunction.name!!

    override val text: String? = psiFunction.text

    override val containingClass: PsiClassWrapper? = psiFunction.run {
        parentOfType<KtClass>()?.let { KotlinPsiClassWrapper(it) }
    }

    override val containingFile: PsiFile = psiFunction.containingFile

    override val methodDescriptor: String
        get() = psiFunction.run {
            val parameterTypes = valueParameters.joinToString("") { generateFieldType(it.typeReference) }
            val returnType = generateReturnDescriptor(psiFunction)
            return "$name($parameterTypes)$returnType"
        }

    override val signature: String
        get() = buildSignature(psiFunction)

    override val parameterNames: List<String>
        get() = psiFunction.valueParameters.map { it.name ?: "" }

    override val parameterTypes: List<String>
        get() = psiFunction.valueParameters.map { it.typeReference?.text ?: "Any" }

    override val returnType: String
        get() = psiFunction.typeReference?.text ?: "Unit"

    val parameterList = psiFunction.valueParameterList

    val isPrimaryConstructor: Boolean = psiFunction is KtPrimaryConstructor

    val isSecondaryConstructor: Boolean = psiFunction is KtSecondaryConstructor

    val isTopLevelFunction: Boolean = psiFunction.containingClassOrObject == null

    val isDefaultMethod: Boolean = psiFunction.run {
        val containingClass = PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java)
        val containingInterface = containingClass?.isInterfaceClass()
        // ensure that the function is a non-abstract method defined in an interface
        name != "<init>" && // function is not a constructor
                bodyExpression != null && // function has an implementation
                containingInterface == true // function is defined within an interface
    }

    override fun containsLine(lineNumber: Int): Boolean {
        val psiFile = psiFunction.containingFile
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: return false
        val textRange = psiFunction.textRange
        // increase by one is necessary due to different start of numbering
        val startLine = document.getLineNumber(textRange.startOffset) + 1
        // increase by one is necessary due to different start of numbering
        val endLine = document.getLineNumber(textRange.endOffset) + 1
        return lineNumber in startLine..endLine
    }

    /**
     * Returns a set of `PsiClassWrapper` instances for non-standard Kotlin classes referenced by the
     * parameters of the current function.
     *
     * @return A mutable set of `PsiClassWrapper` instances representing non-standard Kotlin classes.
     */
    fun getInterestingPsiClassesWithQualifiedNames(): MutableSet<PsiClassWrapper> {
        val interestingPsiClasses = mutableSetOf<PsiClassWrapper>()

        psiFunction.valueParameters.forEach { parameter ->
            val typeReference = parameter.typeReference
            val psiClass = PsiTreeUtil.getParentOfType(typeReference, KtClass::class.java)
            if (psiClass != null && psiClass.fqName != null && !psiClass.fqName.toString().startsWith("kotlin.")) {
                interestingPsiClasses.add(KotlinPsiClassWrapper(psiClass))
            }
        }

        return interestingPsiClasses
    }

    /**
     * Generates the return descriptor for a method.
     *
     * @param psiFunction the function
     * @return the return descriptor
     */
    private fun generateReturnDescriptor(psiFunction: KtFunction): String {
        val returnType = psiFunction.typeReference?.text ?: "Unit"
        return generateFieldType(returnType)
    }

    /**
     * Generates the field descriptor for a type.
     *
     * @param typeReference the type reference to generate the descriptor for
     * @return the field descriptor
     */
    private fun generateFieldType(typeReference: KtTypeReference?): String {
        val type = typeReference?.text ?: "Unit"
        return generateFieldType(type)
    }

    /**
     * Generates the field descriptor for a type.
     * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3
     *
     * @param type the type to generate the descriptor for
     * @return the field descriptor
     */
    private fun generateFieldType(type: String): String {
        return when (type) {
            "Int" -> "I"
            "Long" -> "J"
            "Float" -> "F"
            "Double" -> "D"
            "Boolean" -> "Z"
            "Byte" -> "B"
            "Char" -> "C"
            "Short" -> "S"
            "Unit" -> "V"
            else -> "L${type.replace('.', '/')};"
        }
    }

    companion object {
        /**
         * Builds a signature for a given Kotlin function by extracting the method body portion.
         *
         * @param function The Kotlin function to build the signature for.
         * @return The signature of the function.
         */
        fun buildSignature(function: KtFunction) = function.run {
            val bodyStart = bodyExpression?.startOffsetInParent ?: textLength
            text.substring(0, bodyStart).replace('\n', ' ').trim()
        }
    }
}
