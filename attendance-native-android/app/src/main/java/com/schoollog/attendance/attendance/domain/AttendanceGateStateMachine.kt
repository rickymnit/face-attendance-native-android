package com.schoollog.attendance.attendance.domain

class AttendanceGateStateMachine(
    initialState: GateModeState = GateModeState.IdleWaitingForFace,
) {
    var currentState: GateModeState = initialState
        private set

    fun transition(event: GateModeEvent): GateModeState {
        currentState = reduce(currentState, event)
        return currentState
    }

    fun reset(): GateModeState = transition(GateModeEvent.ResetForNextStudent)

    private fun reduce(
        state: GateModeState,
        event: GateModeEvent,
    ): GateModeState =
        when (event) {
            GateModeEvent.ResetForNextStudent -> GateModeState.IdleWaitingForFace
            GateModeEvent.Timeout -> GateModeState.ManualReviewRequired("Timed out waiting for attendance gates")
            is GateModeEvent.FaceQualityFailed -> when (state) {
                GateModeState.FaceDetected,
                GateModeState.FaceQualityChecking,
                GateModeState.HoldStill -> GateModeState.FaceQualityChecking
                else -> state
            }
            is GateModeEvent.LivenessFailed -> GateModeState.ManualReviewRequired(event.reason)
            is GateModeEvent.MatchNotFound -> GateModeState.ManualReviewRequired(event.reason)
            is GateModeEvent.DuplicateFound -> GateModeState.DuplicateScan(event.student)
            is GateModeEvent.AttendanceSaved -> GateModeState.AttendanceMarked(event.student)
            GateModeEvent.FrameReceived -> when (state) {
                GateModeState.FaceDetected -> GateModeState.FaceQualityChecking
                else -> state
            }
            GateModeEvent.FaceDetected -> when (state) {
                GateModeState.IdleWaitingForFace -> GateModeState.FaceDetected
                else -> state
            }
            GateModeEvent.FaceQualityPassed -> when (state) {
                GateModeState.FaceDetected,
                GateModeState.FaceQualityChecking -> GateModeState.HoldStill
                else -> state
            }
            GateModeEvent.FaceStable -> when (state) {
                GateModeState.HoldStill -> GateModeState.CheckingLiveness
                else -> state
            }
            GateModeEvent.LivenessPassed -> when (state) {
                GateModeState.CheckingLiveness -> GateModeState.GeneratingEmbeddingPlaceholder
                else -> state
            }
            GateModeEvent.EmbeddingGeneratedPlaceholder -> when (state) {
                GateModeState.GeneratingEmbeddingPlaceholder -> GateModeState.MatchingStudentPlaceholder
                else -> state
            }
            is GateModeEvent.MatchFound -> when (state) {
                GateModeState.MatchingStudentPlaceholder -> GateModeState.MatchingStudentPlaceholder
                else -> state
            }
        }
}
