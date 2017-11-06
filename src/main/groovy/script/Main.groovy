package script

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.patch.FileHeader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk

import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage
import javax.sound.midi.Synthesizer

/**
 *
 */
class Main {
  static RevCommit lastCommit = null;
  static void main(String [] args){

    if(args.size() < 1){
      println "play-git /Users/kyonmm/repos/sample-repo"
    }

    def repositoryPath = args[0].trim()
    def repository = new FileRepository(new File(repositoryPath + "/.git"))

    def git = new Git(repository)

    if( ! repository.getRepositoryState().canResetHead() ){
      println("target path is not a valid repository")
      System.exit(1);
    }

    def musicList = []

    RevWalk walk = new RevWalk(repository);
    while(true){
      git.pull().call().toString()
      RevCommit fromCommit = walk.parseCommit(repository.resolve("HEAD"));
      if(lastCommit != null && lastCommit.id == fromCommit.id){
        sleep 60_000
        continue
      }
      lastCommit = fromCommit
      println fromCommit.id
      RevCommit toCommit = walk.parseCommit(repository.resolve("HEAD^^"));
      RevTree fromTree = fromCommit.getTree();
      RevTree toTree = toCommit.getTree();
      DiffFormatter diffFormatter = new DiffFormatter(System.out);
      diffFormatter.setRepository(repository);
      List<DiffEntry> list = diffFormatter.scan(toTree, fromTree);
      list.each{diffEntry ->
        FileHeader header = diffFormatter.toFileHeader(diffEntry)
        header.toEditList().each{
          def s = diffEntry.newPath.size()
          musicList << [s, it.endA + it.beginA, it.endB + it.beginB].findAll{ 0 < it }
        }
      }
      walk.dispose();

      Synthesizer synthesizer = MidiSystem.getSynthesizer();
      synthesizer.open();
      Receiver receiver = synthesizer.getReceiver();
      ShortMessage message = new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, musicList.flatten().sum() % 128, 0)
      receiver.send(message, -1)

      def before1 = 60
      def before2 = 62
      musicList.eachWithIndex {lines, i ->
        lines.each{
          def o = it <= 127 ? it : it % 127
          message.setMessage(ShortMessage.NOTE_ON, before1, 127);
          message.setMessage(ShortMessage.NOTE_ON, before2, 127);
          message.setMessage(ShortMessage.NOTE_ON, o, 127);
          receiver.send(message, -1);
          sleep(300)
          before1 = before2
          before2 = o
        }
      }
      def after1 = 60
      def after2 = 62
      2.times{
        message.setMessage(ShortMessage.NOTE_ON, before1, 127);
        message.setMessage(ShortMessage.NOTE_ON, before2, 127);
        message.setMessage(ShortMessage.NOTE_ON, after1, 127);
        receiver.send(message, -1);
        sleep(300)
        before1 = before2
        before2 = after1
        after1 = after2
      }
      sleep(2_000)
      receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 0x78, 100), -1);
      sleep(1_000)
      receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 0x78, 80), -1);
      sleep(1_000)
      receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 0x78, 60), -1);
      sleep(1_000)
      receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 0x78, 0), -1);
    }

  }
}
