(project-shell "*shell*" "/home/jan/work/edeposit.amqp.kramerius")
(project-shell "*cmd*" "/home/jan/work/edeposit.amqp.kramerius")
(project-shell "*tests*" "/home/jan/work/edeposit.amqp.kramerius")

(project-task send-message "*cmd*" "./send-message.sh")
(project-task run-amqp "*shell*" "lein run -- --amqp")

(defun restart-app ()
  (interactive)
  (cider-interactive-eval "(ns user)(reset)"))

(defun send-refresh ()
  (interactive)
  (cider-interactive-eval "(ns user)(refresh)"))
