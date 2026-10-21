All questions & problems marked with `TODO`

Libraries:
1. junit-platform-console-standalone-1.11.3.jar

WorkspacePreparation
1. Question attempt has attempt id (studentid + qnsid)
2. Question definition is a folder?, contain tester defination

Dependencies Scenario:
1. precompile class & .jar will be in QuestionDefination or TesterDefination??
2. Qb rely on Qa code -> solution, assume Tester or QesutionDefination provides it

Tester File
current tester file result
```
-------------------------------------------------------
---------------------- Q1a ----------------------------
-------------------------------------------------------
Test 1: getIsogramWords([alice, ben, cathy])
Expected  :|[alice, ben, cathy]|
Actual    :|[alice, ben, cathy]|
Passed
-------------------------------------------------------
Test 2: getIsogramWords([ellen, alan, zoe])
Expected  :|[zoe]|
Actual    :|[zoe]|
Passed
-------------------------------------------------------
Test 3: getIsogramWords([Elle, alan, Daniel, Canny])
Expected  :|[Daniel]|
Actual    :|[Daniel]|
Passed
-------------------------------------------------------
3.0
```
Has test id
passed or failed
return final mark

We will assume:
1. has test id with strict format of 'Test xx:'
2. only one passed or failed statement mentioned in result
3. each passed test case worth 1 mark

Infinite loop problem:
They can have multiple test cases, which each test case is in one try block.
If one block end up in infinite loop, the rest of the blocks below will not run.
Solution:
1. assume timeout result in 0, regardless whether other test case succeed
2. record marks until timeout, if timeout happen, will still keep record of previous success pass case and award marks, ignore all remaining test case after the timeout (current approach)
3. run each test case separately by separating them into different file and execute them separately (increase thread use, each timeout == 1 mark loss)
