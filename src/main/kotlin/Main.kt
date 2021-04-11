import java.math.BigInteger

abstract class ExpressionToken

class Assignment(val left: String, val right: String)

class InvalidIdentifier : Exception("Invalid identifier")

class UnknownVariable : Exception("Unknown variable")

class UnknownBinaryOperator(message: String) : Exception(message)

class NoMatchingBracket : Exception("Couldn't find a matching bracket")

data class BigIntConstantTk(val value: BigInteger) : ExpressionToken()

data class OperatorTk(val value: String) : ExpressionToken() {
    val priority = when (value.getOrElse(0) { ' ' }) {
        in "+-" -> 1
        in "*/" -> 2
        else -> 0
    }

    fun isHigherPrecedence(other: OperatorTk) = priority > other.priority
}

data class VariableNameTk(val value: String) : ExpressionToken()

data class RoundBracketTk(val value: Char) : ExpressionToken()

data class Variable(val name: String, val value: BigInteger)

val ID_REGEX = Regex("[a-zA-Z]+")

val COMMANDS = mapOf(
    "/exit" to { executeTerminateOperation { println("Bye!") } },
    "/help" to { println("The program evaluates basic arithmetic expressions") }
)

// GLOBAL STATE DEFINITION
var shouldFinish = false
fun executeTerminateOperation(f: () -> Unit) {
    f()
    shouldFinish = true
}

var nameToVariable = mutableMapOf<String, Variable>()
// END GLOBAL STATE DEFINITION

fun main() {
    while (!shouldFinish) {
        val inputLine = readLine() ?: ""
        if (isCommand(inputLine)) COMMANDS[inputLine]?.invoke() ?: println("Unknown command")
        else handleAssignmentOrExpression(inputLine)
    }
}

fun handleAssignmentOrExpression(line: String) {
    if (line.isNotBlank()) {
        when (val assignment = parseAssignment(line)) {
            null -> handleExpression(line)
            else -> handle(assignment)
        }
    }
}

fun handleExpression(str: String) {
    val invalidExpressionMessage = "Invalid expression"
    val expression = withoutWhitespaces(str)
    val tokens = tokenize(expression)
    val infixNotation = try {
        infixToPostfix(tokens)
    } catch (e: NoMatchingBracket) {
        println(invalidExpressionMessage)
        return
    }
    println(
        try {
            evaluateInfixExpression(infixNotation).toString()
        } catch (ex: Exception) {
            when (ex) {
                is UnknownVariable -> ex.message
                else -> invalidExpressionMessage
            }
        }
    )
}

@Throws(InvalidIdentifier::class)
fun handle(assignment: Assignment) {
    try {
        val name = assignment.left
        if (!isIdentifier(name)) throw InvalidIdentifier()
        nameToVariable[name] = Variable(name, rightPartOfAssignmentToBigInt(assignment.right))
    } catch (ex: Exception) {
        println(ex.message)
    }
}

@Throws(Exception::class, UnknownVariable::class)
fun rightPartOfAssignmentToBigInt(assignmentRightPart: String): BigInteger {
    return if (isIdentifier(assignmentRightPart)) {
        nameToVariable[assignmentRightPart]?.value ?: throw UnknownVariable()
    } else { // when value is a constant
        assignmentRightPart.toBigIntegerOrNull() ?: throw Exception("Invalid assignment")
    }
}

fun parseAssignment(line: String): Assignment? {
    val found = Regex("(.*)=(.*)").find(line)?.groupValues ?: listOf()
    return if (found.size == 3) Assignment(found[1].trim(), found[2].trim()) else null
}

@Throws(UnknownVariable::class, UnknownBinaryOperator::class)
fun evaluateInfixExpression(tokens: MutableList<ExpressionToken>): BigInteger {
    val tmpStack = mutableListOf<BigInteger>()
    for (token in tokens) {
        when (token) {
            is VariableNameTk -> tmpStack.add(tokenToValueOfVariable(token) ?: throw UnknownVariable())
            is BigIntConstantTk -> tmpStack.add(token.value)
            is OperatorTk -> tmpStack.add(applyUnaryOrBinaryOperator(tmpStack, token))
        }
    }
    return tmpStack.removeLast()
}

fun tokenToValueOfVariable(token: VariableNameTk): BigInteger? =
    nameToVariable[token.value]?.value

// get left and right operands from the stack
fun pop2Operands(stack: MutableList<BigInteger>): List<BigInteger?> =
    Array(2) { stack.removeLastOrNull() }.reversed()

@Throws(UnknownBinaryOperator::class)
fun applyUnaryOrBinaryOperator(infixElementsStack: MutableList<BigInteger>, operator: OperatorTk): BigInteger {
    val (leftOperand, rightOperand) = pop2Operands(infixElementsStack)
    return if (leftOperand != null) applyBinaryOperator(leftOperand, rightOperand!!, operator)
    else applyUnaryOperator(rightOperand!!, operator)
}

fun applyUnaryOperator(value: BigInteger, token: OperatorTk): BigInteger =
    if (token.value == "-") value * BigInteger.valueOf(-1) else value

@Throws(UnknownBinaryOperator::class)
fun applyBinaryOperator(left: BigInteger, right: BigInteger, token: OperatorTk): BigInteger {
    return when (token.value) {
        "+" -> left + right
        "-" -> left - right
        "*" -> left * right
        "/" -> left / right
        else -> throw UnknownBinaryOperator("Unknown binary operator: ${token.value}")
    }
}

@Throws(NoMatchingBracket::class)
fun infixToPostfix(tokens: MutableList<ExpressionToken>): MutableList<ExpressionToken> {
    val resultStack = mutableListOf<ExpressionToken>()
    val stackOfParenthesizedGroupsOfOperators = mutableListOf<MutableList<OperatorTk>>(mutableListOf())

    var openingBracketsAmount = 0
    fun openParenthesizedExpression() = ++openingBracketsAmount

    @Throws(NoMatchingBracket::class)
    fun closeParenthesizedExpression() =
        if (openingBracketsAmount > 0) --openingBracketsAmount else throw NoMatchingBracket()

    for (token in tokens) {
        when (token) {
            is VariableNameTk -> resultStack.add(token)
            is BigIntConstantTk -> resultStack.add(token)
            is OperatorTk -> {
                resultStack.addAll(
                    popAllMatchingPredicate(
                        stackOfParenthesizedGroupsOfOperators.last()
                    ) { !token.isHigherPrecedence(it) })
                stackOfParenthesizedGroupsOfOperators.last().add(token)
            }
            is RoundBracketTk -> {
                if (token.value == '(') {
                    openParenthesizedExpression()
                    stackOfParenthesizedGroupsOfOperators.add(mutableListOf())
                } else {
                    closeParenthesizedExpression()
                    resultStack.addAll(stackOfParenthesizedGroupsOfOperators.removeLast().reversed())
                }
            }
        }
    }
    if (openingBracketsAmount != 0) throw NoMatchingBracket()
    resultStack.addAll(stackOfParenthesizedGroupsOfOperators.first().reversed())
    return resultStack
}

fun <T> popAllMatchingPredicate(stack: MutableList<T>, predicate: (T) -> Boolean): MutableList<T> {
    var lastElement = stack.lastOrNull()
    val poppedTokens: MutableList<T> = mutableListOf()
    while (lastElement != null && predicate(lastElement)) {
        poppedTokens.add(stack.removeLast())
        lastElement = stack.lastOrNull()
    }
    return poppedTokens
}

fun tokenize(expression: String): MutableList<ExpressionToken> {
    val tokens = mutableListOf<ExpressionToken>()
    findAllMatchedTokens(expression) { tokens.add(it) }
    return tokens
}

fun findAllMatchedTokens(expression: String, tokenHandler: (ExpressionToken) -> Unit) {
    val tokenAndMatch = findExpressionTokenWithMatchResult(expression)
    if (tokenAndMatch != null) {
        val indexAfterTokenValue = tokenAndMatch.second.range.last + 1
        tokenHandler(tokenAndMatch.first)
        findAllMatchedTokens(expression.substring(indexAfterTokenValue), tokenHandler)
    }
}

fun findExpressionTokenWithMatchResult(expression: String): Pair<ExpressionToken, MatchResult>? {
    return expression.let { exp ->
        findRoundBracket(exp)?.let { RoundBracketTk(it.value[0]) to it }
            ?: findOperator(exp)?.let { OperatorTk(reduceAdmissibleOperatorIfNecessary(it.value)) to it }
            ?: findVariableName(exp)?.let { VariableNameTk(it.value) to it }
            ?: findIntWithoutSign(exp)?.let { BigIntConstantTk(it.value.toBigInteger()) to it }
    }
}

fun reduceAdmissibleOperatorIfNecessary(operator: String): String {
    if (Regex("\\++").matches(operator)) return "+"
    else if (Regex("-+").matches(operator)) {
        val minusesAmount = operator.count { it == '-' }
        return if (minusesAmount % 2 == 0) "+" else "-"
    }
    return operator
}

fun findVariableName(s: String): MatchResult? {
    if (s.isBlank()) return null
    val startsAsVariableName = ID_REGEX.matches(s[0].toString())
    return if (startsAsVariableName) ID_REGEX.find(s)!!
    else null
}

fun findOperator(s: String): MatchResult? {
    val operatorPattern = "[+\\-*/]"
    val startsWithOperator = s.isNotBlank() && s[0].toString().matches(Regex(operatorPattern))
    return if (!startsWithOperator) null
    else Regex("$operatorPattern+").find(s)
}

fun findRoundBracket(s: String): MatchResult? {
    return if (s.isBlank()) null
    else Regex("[()].*").find(s[0].toString())
}

fun findIntWithoutSign(s: String): MatchResult? {
    val intPattern = "\\d+"
    val startsWithDigit = s.isNotBlank() && s[0].isDigit()
    return if (startsWithDigit) Regex(intPattern).find(s)
    else null
}

fun isIdentifier(str: String): Boolean = ID_REGEX.matches(str)

fun isCommand(str: String): Boolean = str.startsWith("/")

fun withoutWhitespaces(s: String): String = s.replace("\\s".toRegex(), "")