import { setupServer } from 'msw/node';
import { handlers } from '/home/paul/project/sei-online-code/frontend/src/mocks/handlers.ts';

const server = setupServer(...handlers);
server.listen();

async function test() {
  // create a requirement to get ids
  const createRes = await fetch('http://localhost/mocker.api/sei-online-code/api/requirement/save', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectId: 'PRJ0001', title: 'Test Req', description: 'desc' }),
  });
  const createJson = await createRes.json();
  console.log('create', createRes.status, JSON.stringify(createJson).slice(0,200));
  const reqId = createJson.data.id;

  // get requirement
  const getRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/requirement/findOne?id=${reqId}`);
  const getJson = await getRes.json();
  console.log('get req', getRes.status, getJson.success);

  // edit PRD
  const editPrdRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/requirement/${reqId}/editPrd`, {
    method: 'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({prdContent:'# PRD'})
  });
  console.log('editPrd', editPrdRes.status, (await editPrdRes.json()).success);

  // confirm PRD -> overview design
  const confirmPrdRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/requirement/${reqId}/confirmPrd`, {method:'POST'});
  const confirmPrdJson = await confirmPrdRes.json();
  console.log('confirmPrd', confirmPrdRes.status, confirmPrdJson.success, confirmPrdJson.data?.status);
  const ovId = confirmPrdJson.data?.overviewDesignId || confirmPrdJson.data?.id;

  // get overview design by requirementId
  const ovRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/overview-design/findOne?requirementId=${reqId}`);
  const ovJson = await ovRes.json();
  console.log('overview findOne', ovRes.status, ovJson.success, ovJson.data?.id);

  // edit overview
  const ovEditRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/overview-design/${ovJson.data.id}/edit`, {
    method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({content:'# Overview'})
  });
  console.log('overview edit', ovEditRes.status, (await ovEditRes.json()).success);

  // confirm overview -> detailed design
  const ovConfirmRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/overview-design/${ovJson.data.id}/confirm`, {method:'POST'});
  const ovConfirmJson = await ovConfirmRes.json();
  console.log('overview confirm', ovConfirmRes.status, ovConfirmJson.success, ovConfirmJson.data?.status);

  // get detailed designs
  const ddListRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/detailed-design/findByOverview?overviewDesignId=${ovJson.data.id}`);
  const ddListJson = await ddListRes.json();
  console.log('dd list', ddListRes.status, ddListJson.success, ddListJson.data?.length);
  const ddId = ddListJson.data?.[0]?.id;

  if (ddId) {
    // edit detailed design
    const ddEditRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/detailed-design/${ddId}/edit`, {
      method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({content:'# Detailed'})
    });
    console.log('dd edit', ddEditRes.status, (await ddEditRes.json()).success);

    // confirm detailed -> coding task
    const ddConfirmRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/detailed-design/${ddId}/confirm`, {method:'POST'});
    const ddConfirmJson = await ddConfirmRes.json();
    console.log('dd confirm', ddConfirmRes.status, ddConfirmJson.success, ddConfirmJson.data?.status);

    // coding task list
    const ctRes = await fetch('http://localhost/mocker.api/sei-online-code/api/coding-task/findByPage', {
      method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({filters:[{fieldName:'requirementId', value:reqId, operator:'EQ'}]})
    });
    const ctJson = await ctRes.json();
    console.log('coding tasks', ctRes.status, ctJson.success, ctJson.data?.rows?.length);
    const taskId = ctJson.data?.rows?.[0]?.id;

    if (taskId) {
      // run task
      const runRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/coding-task/${taskId}/run`, {
        method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({userPrompt:null})
      });
      const runJson = await runRes.json();
      console.log('run task', runRes.status, runJson.success, runJson.data?.status);

      // run history
      const histRes = await fetch(`http://localhost/mocker.api/sei-online-code/api/run/findByCodingTask?codingTaskId=${taskId}`);
      const histJson = await histRes.json();
      console.log('run history', histRes.status, histJson.success, histJson.data?.length);
    }
  }

  server.close();
}

test().catch((e)=>{ console.error(e); server.close(); process.exit(1); });
