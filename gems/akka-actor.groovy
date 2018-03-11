@Grab(group='com.typesafe.akka', module='akka-actor_2.12', version='2.5.11')

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.UntypedAbstractActor
import akka.routing.RoundRobinPool
import groovy.transform.Immutable

class PI {
    @Immutable
    static class Calculate { }

    @Immutable
    static class Work {
        int start
        int nrOfElements
    }

    @Immutable
    static class Result {
        double value
    }

    @Immutable
    static class PiApproximation {
        double pi
        Long duration
    }

    static class Worker extends UntypedAbstractActor {
        @Override
        void onReceive(Object message) {
            switch(message) {
                case Work:
                    def work = message as Work
                    getSender().tell(new Result(calculatePiFor(work.start, work.nrOfElements)), getSelf())
                    break

                default:
                    unhandled(message)
            }
        }

        private double calculatePiFor(int start, int nrOfElements) {
            double acc = (start * nrOfElements ..< (start+1) * nrOfElements).inject(0.0) {
                acc, it -> acc += 4.0 * (1 - (it % 2) * 2) / (2 * it + 1)
            }
            return acc
        }
    }

    static class Master extends UntypedAbstractActor {
        final int nrOfMessages
        final int nrOfElements
        final ActorRef listener
        final ActorRef workerRouter
        final long start = System.currentTimeMillis()

        double pi
        int nrOfResults

        Master(final int nrOfWorkers, int nrOfMessages, int nrOfElements, ActorRef listener) {
            this.nrOfMessages = nrOfMessages
            this.nrOfElements = nrOfElements
            this.listener = listener

            this.workerRouter = this.getContext().actorOf(Props.create(Worker.class).withRouter(new RoundRobinPool(nrOfWorkers)))
        }

        @Override
        void onReceive(Object message) {
            switch(message) {
                case Calculate:
                    nrOfMessages.times { workerRouter.tell(new Work(it, nrOfElements), getSelf()) }
                    break

                case Result:
                    def result = message as Result
                    pi += result.value
                    nrOfResults++

                    if(nrOfResults == nrOfMessages) {
                        def duration = System.currentTimeMillis() - start
                        listener.tell(new PiApproximation(pi, duration), getSelf())
                        getContext().stop(getSelf())
                    }
                    break

                default:
                    unhandled(message)
            }
        }
    }

    static class Listener extends UntypedAbstractActor {
        @Override
        void onReceive(Object message) {
            switch (message) {
                case PiApproximation:
                    def approximation = message as PiApproximation
                    println "\n\tPi approximation: \t\t{$approximation.pi}\n\tCaculation time: \t{$approximation.duration} ms"
                    getContext().system().terminate()
                    break
                default:
                    unhandled(message)
            }
        }
    }

    public static void main(String[] args) {
        PI pi = new PI()
        pi.calculate(4, 10000, 10000)
    }

    public void calculate(final int nrOfWorkers, final int nrOfElements, final int nrOfMessages) {
        def system = ActorSystem.create("pi")

        final ActorRef listener = system.actorOf(Props.create(Listener.class), "listener")
        ActorRef master = system.actorOf(Props.create(Master.class, nrOfWorkers, nrOfMessages, nrOfElements, listener), "master")

        master.tell(new Calculate(), ActorRef.noSender())
    }
}
