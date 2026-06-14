package io.github.mesmerprism.questquestionnaire.contract

internal val Expected = QuestionnaireExpectedResult(
    requestId = "f8d62b0a-77e8-4f7d-a7da-7f95fd9a7024",
    nonce = "b96d9b51c4874db8a4e8f1b4",
    questionnaireId = "brb-questionnaire-v1",
    stage = "post_condition:pictographic",
    screenSequence = listOf(
        "post_condition:pictographic",
        "post_condition:presence_questionnaire",
        "post_condition:lost_opportunity"
    )
)

internal val ExpectedGeneric = QuestionnaireExpectedResult(
    requestId = "8d3f2634-5c03-4e98-9578-5dd3cde5301e",
    nonce = "e84e418ca0a6479a94973a0c",
    questionnaireId = "generic-questionnaire-v1",
    stage = "generic:intro",
    screenSequence = listOf(
        "generic:intro",
        "generic:rating",
        "generic:comment",
        "generic:complete"
    )
)

internal fun fixture(name: String): String {
    val resource = Thread.currentThread().contextClassLoader.getResource(name)
        ?: error("Missing fixture resource: $name")
    return resource.readText(Charsets.UTF_8)
}
