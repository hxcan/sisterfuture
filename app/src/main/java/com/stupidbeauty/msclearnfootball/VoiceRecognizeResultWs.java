package com.stupidbeauty.msclearnfootball;

import java.util.ArrayList;

@SuppressWarnings({"EmptyMethod", "unused"})
class VoiceRecognizeResultWs
{
	public ArrayList<VoiceRecognizeResultWsCw> getCw() {
		return cw;
	}

	/**
	 * 获取说出的内容。

	 * @return 说出的内容。
	 */
	public String getFirstSaidText()
	{
		return cw.get(0).getW();
	} //public String getFirstSaidText()

	/**
	 * 获取文字内容。
	 * @return 文字内容。
	 */
	public String getSaidText()
	{
		StringBuilder result= new StringBuilder(); //结果。

		for(VoiceRecognizeResultWsCw currentCw:cw) //一个个地处理。
		{
			String currentText=currentCw.getW(); //获取内容。

			result.append(currentText); //追加。
		} //for(VoiceRecognizeResultWsCw currentCw:cw) //一个个地处理。

		return result.toString();
	} //public String getSaidText()

	private ArrayList<VoiceRecognizeResultWsCw> cw; //词语列表。

}
