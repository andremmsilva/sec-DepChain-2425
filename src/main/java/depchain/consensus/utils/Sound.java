package depchain.consensus.utils;

import depchain.config.DepChainConfig;
import depchain.network.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/*
 * Represents the sound(-) predicate, according to
 * Introduction to Reliable and Secure Distributed Programming - Algorithms 5.16-5.18
 * */
public class Sound {
    private final List<State> states = new ArrayList<>();
    private final Predicate<List<Message>> sound = l -> {
        l.forEach(msg -> this.states.add(State.fromStr(msg.getPayload())));
        return this.states.stream().anyMatch(s -> this.binds(s.getLastRound(), this.states)) ||
                this.unbound(states);
    };

    public Sound() {
    }

    public Predicate<List<Message>> getPredicate() {
        return sound;
    }

    private boolean quorumHighest(Tuple testee, List<State> states) {
        int quorumCount = 0;

        // First, check if there is an entry matching (ts, v)
        boolean pairExists = states.stream().anyMatch(
                entry -> entry.getLastRound().getTimestamp() == testee.getTimestamp() &&
                        entry.getLastRound().getValue().equals(testee.getValue()));

        if (!pairExists) {
            return false; // Early exit if no matching pair
        }

        // Count entries satisfying the quorum condition
        for (State state : states) {
            if (state.getLastRound().getTimestamp() < testee.getTimestamp() ||
                    (state.getLastRound().getTimestamp() == testee.getTimestamp() &&
                            state.getLastRound().getValue().equals(testee.getValue()))) {
                quorumCount++;
            }
        }

        // Check if the quorum condition is met
        return quorumCount > (DepChainConfig.PROCESSES + DepChainConfig.FAIL_BOUND) / 2;
    }

    private boolean certifiedValue(Tuple testee, List<State> states) {
        int quorumCount = 0;

        // First, check if there is an entry matching (ts, v)
        boolean pairExists = states.stream().anyMatch(
                entry -> entry.getLastRound().getTimestamp() == testee.getTimestamp() &&
                        entry.getLastRound().getValue().equals(testee.getValue()));

        if (!pairExists) {
            return false; // Early exit if no matching pair
        }

        // Count entries satisfying the quorum condition
        for (State state : states) {
            if (state.getWriteSet().stream().anyMatch(t -> t.getTimestamp() >= testee.getTimestamp() &&
                    Objects.equals(t.getValue(), testee.getValue()))) {
                quorumCount++;
            }
        }

        // Check if the quorum condition is met
        return quorumCount > DepChainConfig.FAIL_BOUND;
    }

    public boolean binds(Tuple lastRound, List<State> states) {
        return this.quorumHighest(lastRound, states) && this.certifiedValue(lastRound, states);
    }

    public boolean unbound(List<State> states) {
        return states.size() >= DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND &&
                states.stream().filter(s -> s.getLastRound().getTimestamp() == 0).toList().size() >
                        (DepChainConfig.PROCESSES + DepChainConfig.FAIL_BOUND) / 2;
    }
}
