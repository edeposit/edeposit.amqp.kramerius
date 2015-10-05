(project-shell "*cmd*" "/home/jan/work/edeposit.amqp.kramerius")
(project-shell "*docs*" "/home/jan/work/edeposit.amqp.kramerius/docs")
(project-shell "*docs-serve*" "/home/jan/work/edeposit.amqp.kramerius/docs")
(project-shell "*shell*" "/home/jan/work/edeposit.amqp.kramerius")
(project-shell "*tests*" "/home/jan/work/edeposit.amqp.kramerius")

(project-task send-message "*cmd*" "./send-message.sh")
(project-task run-amqp "*shell*" "lein run -- --amqp")
(project-task run-docs-make "*docs*" "make html")
(project-task run-docs-serve "*docs-serve*" "python -m SimpleHTTPServer 8000")
(project-task run-nosier-makedocs "*docs*" "nosier -b 'plans' -b '_build' -b '_static' -b '_download' 'make html'")

(project-task run-readdocs "*cmd*" "google-chrome http://localhost:8000/_build/html/index.html&")

(defun restart-app ()
  (interactive)
  (cider-interactive-eval "(ns user)(reset)"))

(defun send-refresh ()
  (interactive)
  (cider-interactive-eval "(ns user)(refresh)"))
